package com.ouroboros.documentmanagement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.ouroboros.documentmanagement.model.PendingContentCleanup;

public interface PendingContentCleanupRepository {
  PendingContentCleanup enqueue(String storagePath);
  Optional<PendingContentCleanup> findById(String id);
  List<PendingContentCleanup> findDue(LocalDateTime now, int limit);
  void failed(PendingContentCleanup item, LocalDateTime nextAttempt, String error);
  void delete(String id);
}
