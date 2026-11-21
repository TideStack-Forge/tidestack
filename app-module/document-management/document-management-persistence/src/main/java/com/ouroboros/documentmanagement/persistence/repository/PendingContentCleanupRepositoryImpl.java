package com.ouroboros.documentmanagement.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.documentmanagement.model.PendingContentCleanup;
import com.ouroboros.documentmanagement.persistence.entity.PendingContentCleanupEntity;
import com.ouroboros.documentmanagement.repository.PendingContentCleanupRepository;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import static com.ouroboros.data.dsl.query.Query.field;

@Repository
public class PendingContentCleanupRepositoryImpl implements PendingContentCleanupRepository {
  private final TypedDataModel<String, PendingContentCleanupEntity> data;
  public PendingContentCleanupRepositoryImpl(TypedDataModel<String, PendingContentCleanupEntity> data) { this.data = data; }
  @Override public PendingContentCleanup enqueue(String key) {
    var entity = new PendingContentCleanupEntity();
    entity.setStoragePath(key); entity.setAttempts(0); entity.setCreatedAt(LocalDateTime.now()); entity.setNextAttemptAt(entity.getCreatedAt());
    return from(data.insert(entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException));
  }
  @Override public Optional<PendingContentCleanup> findById(String id) {
    return data.query().where(field("id").eq(id)).execute().getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().findFirst().map(this::from);
  }
  @Override public List<PendingContentCleanup> findDue(LocalDateTime now, int limit) {
    return data.query().where(field("nextAttemptAt").lte(now)).clause("ORDER", "nextAttemptAt,id").clause("LIMIT", limit)
        .execute().getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().map(this::from).toList();
  }
  @Override public void failed(PendingContentCleanup item, LocalDateTime nextAttempt, String error) {
    var entity = new PendingContentCleanupEntity(); entity.setId(item.id()); entity.setStoragePath(item.storagePath());
    entity.setCreatedAt(item.createdAt()); entity.setAttempts(item.attempts() + 1); entity.setNextAttemptAt(nextAttempt); entity.setLastError(error);
    data.update(item.id(), entity).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }
  @Override public void delete(String id) { data.delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException); }
  private PendingContentCleanup from(PendingContentCleanupEntity item) {
    return new PendingContentCleanup(item.getId(), item.getStoragePath(), item.getAttempts(), item.getNextAttemptAt(), item.getLastError(), item.getCreatedAt());
  }
}
