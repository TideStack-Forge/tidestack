package com.ouroboros.documentmanagement.service.impl;

import com.ouroboros.documentmanagement.storage.service.BlobDocumentStorageService;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ouroboros.data.model.*;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.documentmanagement.model.*;
import com.ouroboros.documentmanagement.persistence.entity.*;
import com.ouroboros.documentmanagement.persistence.repository.*;
import com.ouroboros.documentmanagement.storage.*;
import com.ouroboros.storage.filesystem.FileSystemBlobStoreFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL, real Spring transactions and real content, with failures injected at domain IO boundaries. */
class DocumentContentTransactionTest {
  @TempDir Path root;
  private BlobDocumentStorageService storage;
  private LibraryNodeRepositoryImpl nodes;
  private PendingContentCleanupRepositoryImpl pending;
  private DocumentContentCleanupService cleanup;
  private LibraryContentServiceImpl service;
  private DataSourceTransactionManager manager;
  private TransactionTemplate tx;

  @BeforeEach void setup() {
    // Standalone SQL fixture starts the existing SPI snowflake configuration used by the application.
    new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
    var source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    var metas = TypedModelMetaHelper.scanModelMate("com.ouroboros.documentmanagement.persistence.entity");
    var station = new SqlDataStation("documents", source, metas.values(), MigrationStrategy.DISABLED, "H2");
    var migration = station.migrate(station.getDataModelList());
    assertTrue(migration.isRight(), () -> migration.toString());
    var nodeModel = station.getDataModelList().stream().filter(m -> m.getRawName().equals("doc_library_node")).findFirst().orElseThrow();
    var pendingModel = station.getDataModelList().stream().filter(m -> m.getRawName().equals("doc_content_cleanup")).findFirst().orElseThrow();
    nodes = new LibraryNodeRepositoryImpl(new BaseTypedDataModel<>(nodeModel, LibraryNodeEntity.class));
    pending = new PendingContentCleanupRepositoryImpl(new BaseTypedDataModel<>(pendingModel, PendingContentCleanupEntity.class));
    storage = spy(new BlobDocumentStorageService(new FileSystemBlobStoreFactory().create(Map.of("root-directory", root.toString())), new DocumentContentProperties()));
    manager = new DataSourceTransactionManager(source); tx = new TransactionTemplate(manager);
    cleanup = new DocumentContentCleanupService(pending, nodes, storage, manager);
    service = new LibraryContentServiceImpl(nodes, storage, cleanup);
    nodes.add(DirectoryNode.builder().id("root").libraryId("lib").name("root").createdBy("test").build());
  }
  @AfterEach void close() { if (storage != null) { storage.close(); } }
  private DocumentNode upload(String name) {
    return tx.execute(status -> service.uploadDocument("lib", "root", name, new ByteArrayInputStream("data".getBytes()), 4L, "text/plain", "test"));
  }
  private java.util.List<PendingContentCleanup> records() { return pending.findDue(LocalDateTime.now().plusYears(1), 100); }

  @Test void rollbackRestoresMetadataAndDiscardsPendingDeletionWithoutRemovingContent() {
    var doc = upload("rollback.txt");
    tx.executeWithoutResult(status -> { service.deleteDocument("lib", doc.getId(), "test"); assertEquals(1, records().size()); status.setRollbackOnly(); });
    assertTrue(nodes.findDocumentById(doc.getId()).isPresent()); assertTrue(storage.exists(doc.getStoragePath())); assertTrue(records().isEmpty());
  }
  @Test void committedDeletionFailureKeepsDurableRetryAndNewCoordinatorResumesIt() {
    var doc = upload("retry.txt");
    doThrow(new IllegalStateException("backend unavailable")).when(storage).delete(doc.getStoragePath());
    tx.executeWithoutResult(status -> service.deleteDocument("lib", doc.getId(), "test"));
    assertTrue(nodes.findDocumentById(doc.getId()).isEmpty()); assertTrue(storage.exists(doc.getStoragePath()));
    var item = records().getFirst(); assertEquals(1, item.attempts()); assertTrue(item.lastError().contains("backend unavailable"));
    pending.failed(item, LocalDateTime.now().minusSeconds(1), "retry now");
    doCallRealMethod().when(storage).delete(doc.getStoragePath());
    new DocumentContentCleanupService(pending, nodes, storage, manager).retryPending();
    assertFalse(storage.exists(doc.getStoragePath())); assertTrue(records().isEmpty());
  }
  @Test void sharedReferenceIncludingNodeInRecoverableLibraryPreservesContent() {
    var doc = upload("shared.txt");
    nodes.add(doc.toBuilder().id("shared-reference").libraryId("recoverable-soft-deleted-library").build());
    tx.executeWithoutResult(status -> service.deleteDocument("lib", doc.getId(), "test"));
    assertTrue(storage.exists(doc.getStoragePath())); assertEquals(1, records().size());
    nodes.deleteById("shared-reference");
    var item = records().getFirst(); pending.failed(item, LocalDateTime.now().minusSeconds(1), "reference removed");
    cleanup.retryPending(); assertFalse(storage.exists(doc.getStoragePath())); assertTrue(records().isEmpty());
  }
  @Test void recursiveDeletionRollsBackAllMetadataAndContent() {
    var first = upload("one"); var second = upload("two");
    tx.executeWithoutResult(status -> { service.deleteDirectory("lib", "root", "test"); status.setRollbackOnly(); });
    assertTrue(nodes.findDirectoryById("root").isPresent()); assertTrue(nodes.findDocumentById(first.getId()).isPresent());
    assertTrue(nodes.findDocumentById(second.getId()).isPresent()); assertTrue(storage.exists(first.getStoragePath())); assertTrue(records().isEmpty());
  }
  @Test void uploadFailureAfterContentWriteIsCompensatedOnRollback() {
    var failingNodes = spy(nodes); doThrow(new IllegalStateException("metadata insert failed")).when(failingNodes).add(isA(DocumentNode.class));
    var failingService = new LibraryContentServiceImpl(failingNodes, storage, cleanup);
    assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> failingService.uploadDocument("lib", "root", "new", new ByteArrayInputStream("data".getBytes()), 4L, "text/plain", "test")));
    try (var files = java.nio.file.Files.walk(root)) { assertEquals(0, files.filter(java.nio.file.Files::isRegularFile).count()); }
    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    assertTrue(records().isEmpty());
  }
  @Test void failedUploadCompensationIsDurableAndCanBeRetried() {
    doThrow(new IllegalStateException("storage offline")).when(storage).delete(anyString());
    tx.executeWithoutResult(status -> { service.uploadDocument("lib", "root", "new", new ByteArrayInputStream("data".getBytes()), 4L, "text/plain", "test"); status.setRollbackOnly(); });
    var item = records().getFirst(); assertTrue(storage.exists(item.storagePath())); assertEquals(1, item.attempts());
    doCallRealMethod().when(storage).delete(anyString()); pending.failed(item, LocalDateTime.now().minusSeconds(1), "retry now");
    cleanup.retryPending(); assertTrue(records().isEmpty()); assertFalse(storage.exists(item.storagePath()));
  }
  @Test void writeWithoutTransactionFailsBeforeCreatingContent() {
    assertThrows(RuntimeException.class, () -> service.uploadDocument("lib", "root", "new", new ByteArrayInputStream(new byte[0]), 0L, "text/plain", "test"));
  }
  @Test void failureDuringCommitPreparationCompensatesUploadedContent() {
    assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
      service.uploadDocument("lib", "root", "commit-failure", new ByteArrayInputStream("data".getBytes()), 4L, "text/plain", "test");
      org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
          new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("commit preparation failed"); }
          });
    }));
    assertTrue(nodes.findDocumentsByParentId("root").isEmpty());
    try (var files = java.nio.file.Files.walk(root)) { assertEquals(0, files.filter(java.nio.file.Files::isRegularFile).count()); }
    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
  }

}
