package com.ouroboros.documentmanagement.service.impl;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ouroboros.common.service.outbox.OutboxBackoff;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.PendingContentCleanupRepository;
import com.ouroboros.documentmanagement.storage.StorageService;
import com.ouroboros.exception.InternalException;
import com.ouroboros.lock.DistributedLockManager;

/** Coordinates document metadata transactions and retryable physical content cleanup. */
@Service
public class DocumentContentCleanupService {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentContentCleanupService.class);
  private final PendingContentCleanupRepository pending;
  private final LibraryNodeRepository nodes;
  private final StorageService storage;
  private final TransactionTemplate independentTransaction;
  private final DistributedLockManager locks = DistributedLockManager.getLockManager();

  public DocumentContentCleanupService(PendingContentCleanupRepository pending, LibraryNodeRepository nodes,
      StorageService storage, PlatformTransactionManager transactionManager) {
    this.pending = pending; this.nodes = nodes; this.storage = storage;
    independentTransaction = new TransactionTemplate(transactionManager);
    independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }
  void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new InternalException("Document content changes require an active metadata transaction");
    }
  }
  void afterDeletion(String key) {
    requireTransaction();
    var item = pending.enqueue(key);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { attemptSafely(item.id()); }
    });
  }
  void compensateOnRollback(String key) {
    requireTransaction();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) { compensate(key); }
        else if (status == STATUS_UNKNOWN) { LOG.error("Document transaction outcome is unknown; preserve content for reconciliation: {}", key); }
      }
    });
  }
  private void compensate(String key) {
    try {
      independentTransaction.executeWithoutResult(status -> {
        try { if (!nodes.existsByStoragePath(key)) { storage.delete(key); } }
        catch (RuntimeException failure) {
          var item = pending.enqueue(key);
          pending.failed(item, OutboxBackoff.nextAttemptAt(1), OutboxBackoff.truncateError(failure.toString()));
          LOG.warn("Document upload compensation queued for retry: {}", key, failure);
        }
      });
    } catch (RuntimeException e) { LOG.error("Cannot persist document upload compensation; reconcile content: {}", key, e); }
  }
  @Scheduled(fixedDelayString = "${ouroboros.document-management.cleanup.retry-delay:60000}",
      initialDelayString = "${ouroboros.document-management.cleanup.retry-delay:60000}")
  public void retryPending() {
    for (var item : pending.findDue(LocalDateTime.now(), 100)) { attemptSafely(item.id()); }
  }
  private void attemptSafely(String id) {
    try {
      independentTransaction.executeWithoutResult(status -> {
        var item = pending.findById(id);
        if (item.isEmpty()) { return; }
        var lock = locks.getLock("document-content-cleanup/" + item.get().storagePath()); lock.lock();
        try {
          if (nodes.existsByStoragePath(item.get().storagePath())) {
            pending.failed(item.get(), LocalDateTime.now().plusHours(1), "Content remains referenced by a document node");
            return;
          }
          storage.delete(item.get().storagePath()); pending.delete(id);
        } catch (RuntimeException e) {
          pending.failed(item.get(), OutboxBackoff.nextAttemptAt(item.get().attempts() + 1), OutboxBackoff.truncateError(e.toString()));
          LOG.warn("Document physical deletion will be retried: {}", item.get().storagePath(), e);
        } finally { lock.unlock(); }
      });
    } catch (RuntimeException e) { LOG.error("Document cleanup record remains pending: {}", id, e); }
  }
}
