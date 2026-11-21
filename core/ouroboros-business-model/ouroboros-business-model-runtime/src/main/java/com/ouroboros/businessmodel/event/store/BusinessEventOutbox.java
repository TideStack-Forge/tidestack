package com.ouroboros.businessmodel.event.store;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.action.BusinessActionExecutionScope;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryResult;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryAttempt;
import com.ouroboros.businessmodel.event.BusinessEventExecutionMode;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime;
import com.ouroboros.businessmodel.transaction.BusinessTransactionResources;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.common.service.outbox.OutboxBackoff;
import com.ouroboros.common.service.outbox.OutboxLease;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.DataModelWriteGuard;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Durable leases: atomic database consumers share the acknowledgement transaction; independent effects release the row lock. */
public class BusinessEventOutbox {
  public static final String MODEL_FULL_NAME = "ouroboros.BusinessEventDelivery";
  private static final Logger logger = LoggerFactory.getLogger(BusinessEventOutbox.class);
  private final PlatformTransactionManager manager;
  private final Supplier<BusinessEventSubscriptionRuntime> runtime;
  private final BusinessEventDeliveryProperties properties;
  private final Clock clock;
  private final String owner = UUID.randomUUID().toString();

  public record Claim(String deliveryId, String token) {
  }

  public BusinessEventOutbox(PlatformTransactionManager manager, Supplier<BusinessEventSubscriptionRuntime> runtime,
                              BusinessEventDeliveryProperties properties, Clock clock) {
    this.manager = Objects.requireNonNull(manager);
    this.runtime = Objects.requireNonNull(runtime);
    this.properties = Objects.requireNonNull(properties);
    this.clock = Objects.requireNonNull(clock);
    if (properties.getBatchSize() < 1 || properties.getBatchSize() > 1000
        || properties.getLeaseSeconds() < 1 || properties.getMaxRetryCount() < 0) {
      throw new IllegalArgumentException("Invalid Business Event delivery settings");
    }
  }

  @Scheduled(fixedDelayString = "${ouroboros.business-event.delivery.scan-delay-ms:30000}",
      scheduler = "businessEventTaskScheduler")
  public void sweep() {
    BusinessModelPublication.withPublishedContext(() -> {
      sweepInPublishedContext();
      return null;
    });
  }

  private void sweepInPublishedContext() {
    if (!DataModelCenter.hasSnapshot()) {
      return;
    }
    var now = now();
    Map<String, Object> failed = new LinkedHashMap<>();
    failed.put("status", "FAILED");
    failed.put("retryCount", Map.of("<=", properties.getMaxRetryCount()));
    Map<String, Object> immediate = new LinkedHashMap<>();
    immediate.put("nextAttemptAt", null);
    failed.put("$or", List.of(Map.of("nextAttemptAt", Map.of("<=", now)), immediate));
    // Expired work takes precedence over new facts; all candidate reads are bounded in the database.
    for (var where : List.of(Map.<String, Object>of("status", "PROCESSING", "claimUntil", Map.of("<=", now)),
        failed, Map.<String, Object>of("status", "PENDING"))) {
      for (var row : deliveries().query().select("id").where(where)
          .clause("ORDER", Map.of("createdAt", "asc", "id", "asc"))
          .clause("LIMIT", properties.getBatchSize()).execute()
          .getOrElseThrow(error -> new BusinessException("业务 Event 恢复扫描失败", error))) {
        deliver(String.valueOf(row.get("id")));
      }
    }
  }

  public void deliverEvent(String eventId) {
    BusinessModelPublication.withPublishedContext(() -> {
      deliverEventInPublishedContext(eventId);
      return null;
    });
  }

  private void deliverEventInPublishedContext(String eventId) {
    for (var row : deliveries().query().select("id").where(Map.of("eventId", eventId))
        .clause("LIMIT", properties.getBatchSize()).execute()
        .getOrElseThrow(error -> new BusinessException("业务 Event 唤醒查询失败", error))) {
      deliver(String.valueOf(row.get("id")));
    }
  }

  public void deliver(String id) {
    try {
      claim(id).ifPresent(this::deliverClaimed);
    } catch (RuntimeException error) {
      // Durable pending/processing state is retained; the recovery scan retries storage failures.
      logger.warn("Business Event delivery could not advance: deliveryId={}", id, error);
    }
  }

  public Optional<Claim> claim(String id) {
    return inNewTransaction(guard -> {
      var delivery = lock(id, guard);
      if (delivery.isEmpty() || !claimable(delivery.get())) {
        return Optional.empty();
      }
      var row = delivery.get();
      String token = UUID.randomUUID().toString();
      Map<String, Object> change = new LinkedHashMap<>();
      change.put("status", "PROCESSING");
      change.put("attempt", row.getAttempt() + 1);
      change.put("claimOwner", owner);
      change.put("claimToken", token);
      change.put("claimUntil", now().plusSeconds(properties.getLeaseSeconds()));
      change.put("lastError", null);
      update(id, change);
      return Optional.of(new Claim(id, token));
    });
  }

  public void deliverClaimed(Claim claim) {
    BusinessModelPublication.withPublishedContext(() -> {
      deliverInPublishedContext(claim);
      return null;
    });
  }

  private void deliverInPublishedContext(Claim claim) {
    var attempt = new AtomicReference<BusinessEventDeliveryAttempt>();
    try {
      IndependentDelivery independent = inNewTransaction(guard -> {
        var delivery = lock(claim.deliveryId(), guard).filter(row -> owns(row, claim));
        if (delivery.isEmpty()) { return null; }
        var row = delivery.get();
        var consumer = runtime.get();
        if (consumer != null) {
          consumer.inspectAttempt(row.getConsumerModelFullName(), row.getSubscriptionName()).ifPresent(attempt::set);
        }
        if (BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION.name().equals(row.getExecutionMode())
            || attempt.get() != null && !attempt.get().executionMode().name().equals(row.getExecutionMode())) {
          acknowledge(row.getId(), BusinessEventDeliveryResult.contractBlocked("delivery-execution-mode-changed"), attempt.get());
          return null;
        }
        if (attempt.get() != null && attempt.get().executionMode() == BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT) {
          Map<String, Object> change = new LinkedHashMap<>();
          recordAttempt(change, attempt.get());
          update(row.getId(), change);
          return new IndependentDelivery(row, loadFact(row.getEventId()), consumer, attempt.get());
        }
        BusinessEventDeliveryResult result = consumer == null
            ? BusinessEventDeliveryResult.contractBlocked("subscription-runtime-unavailable")
            : consumer.deliver(loadFact(row.getEventId()), row.getConsumerModelFullName(), row.getSubscriptionName(),
                row.getId(), row.getAttempt(), attempt.get());
        acknowledge(row.getId(), result, attempt.get());
        return null;
      });
      if (independent != null) {
        var row = independent.delivery();
        // The receiver transaction has committed and released its row lock before invoking an external effect.
        var result = independent.runtime().deliver(independent.occurrence(), row.getConsumerModelFullName(),
            row.getSubscriptionName(), row.getId(), row.getAttempt(), independent.attempt());
        inNewTransaction(guard -> {
          lock(claim.deliveryId(), guard).filter(current -> owns(current, claim))
              .ifPresent(current -> acknowledge(current.getId(), result, independent.attempt()));
          return null;
        });
      }
    } catch (com.ouroboros.businessmodel.action.BusinessActionPreconditionException blocked) {
      markPreconditionBlocked(claim, attempt.get());
    } catch (RuntimeException error) {
      markFailure(claim, error.getClass().getName(), attempt.get());
    }
  }

  private record IndependentDelivery(BusinessEventDeliveryModel delivery, BusinessEventOccurrence<?> occurrence,
                                      BusinessEventSubscriptionRuntime runtime, BusinessEventDeliveryAttempt attempt) { }

  private void acknowledge(String deliveryId, BusinessEventDeliveryResult result, BusinessEventDeliveryAttempt attempt) {
    String state = switch (result.status()) {
      case DELIVERED, FILTERED -> "SUCCESS";
      case AUTHORIZATION_BLOCKED -> "AUTHORIZATION_BLOCKED";
      case CONTRACT_BLOCKED -> "CONTRACT_BLOCKED";
      case PRECONDITION_BLOCKED -> "PRECONDITION_BLOCKED";
    };
    Map<String, Object> change = released(state);
    recordAttempt(change, attempt);
    change.put("lastError", result.diagnosticCode());
    change.put("nextAttemptAt", null);
    update(deliveryId, change);
  }

  public boolean redrive(String id) {
    return inNewTransaction(guard -> {
      var row = lock(id, guard);
      if (row.isEmpty() || !List.of("AUTHORIZATION_BLOCKED", "CONTRACT_BLOCKED", "PRECONDITION_BLOCKED", "DEAD").contains(row.get().getStatus())) {
        return false;
      }
      Map<String, Object> change = released("PENDING");
      change.put("retryCount", 0);
      change.put("nextAttemptAt", null);
      change.put("lastError", null);
      update(id, change);
      return true;
    });
  }

  private void markPreconditionBlocked(Claim claim, BusinessEventDeliveryAttempt attempt) {
    inNewTransaction(guard -> {
      var delivery = lock(claim.deliveryId(), guard).filter(row -> owns(row, claim));
      if (delivery.isEmpty()) { return null; }
      Map<String, Object> change = released("PRECONDITION_BLOCKED");
      recordAttempt(change, attempt);
      change.put("lastError", "target-action-precondition-unsatisfied");
      change.put("nextAttemptAt", null);
      update(claim.deliveryId(), change);
      return null;
    });
  }

  private void markFailure(Claim claim, String diagnostic, BusinessEventDeliveryAttempt attempt) {
    inNewTransaction(guard -> {
      var delivery = lock(claim.deliveryId(), guard).filter(row -> owns(row, claim));
      if (delivery.isEmpty()) {
        return null;
      }
      var row = delivery.get();
      int next = row.getRetryCount() + 1;
      boolean dead = OutboxBackoff.isDead(next, properties.getMaxRetryCount());
      Map<String, Object> change = released(dead ? "DEAD" : "FAILED");
      recordAttempt(change, attempt);
      change.put("retryCount", next);
      change.put("nextAttemptAt", dead ? null : OutboxBackoff.nextAttemptAt(row.getRetryCount(), now()));
      change.put("lastError", OutboxBackoff.truncateError(diagnostic));
      update(row.getId(), change);
      return null;
    });
  }

  private void recordAttempt(Map<String, Object> change, BusinessEventDeliveryAttempt attempt) {
    change.put("user", attempt == null ? null : attempt.user());
    change.put("attemptRevision", attempt == null ? null : attempt.subscriptionRevision());
    change.put("targetRevision", attempt == null ? null : attempt.targetRevision());
    change.put("implementationRef", attempt == null ? null : attempt.implementationRef());
    change.put("implementationVersion", attempt == null ? null : attempt.implementationVersion());
  }

  private Map<String, Object> released(String status) {
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("status", status);
    change.put("claimOwner", null);
    change.put("claimToken", null);
    change.put("claimUntil", null);
    return change;
  }

  private boolean owns(BusinessEventDeliveryModel row, Claim claim) {
    return "PROCESSING".equals(row.getStatus()) && claim.token().equals(row.getClaimToken());
  }

  private boolean claimable(BusinessEventDeliveryModel row) {
    return OutboxLease.isClaimable(row.getStatus(), row.getRetryCount(), row.getNextAttemptAt(), row.getClaimToken(),
        row.getClaimUntil(), properties.getMaxRetryCount(), now());
  }

  private Optional<BusinessEventDeliveryModel> lock(String id, DataModelWriteGuard guard) {
    var model = deliveries();
    guard.beforeWrite(model);
    var statement = QueryStatement.builder().from(model.getFullName(), model.getRawName())
        .select(model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(SExpression.create(Operators.EQ, SExpression.field("id"), SExpression.constant(id))).build();
    var rows = model.queryForUpdate(statement).getOrElseThrow(error -> new BusinessException("业务 Event 投递锁定失败", error));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0).toBean(BusinessEventDeliveryModel.class));
  }

  private void update(String id, Map<String, Object> change) {
    change.put("updatedAt", now());
    Long affected = deliveries().update(id, change).getOrElseThrow(error -> new BusinessException("业务 Event 投递状态写入失败", error));
    if (affected == null || affected != 1) {
      throw new BusinessException("业务 Event 投递状态写入未更新唯一记录");
    }
  }

  private BusinessEventOccurrence<Map<String, Object>> loadFact(String eventId) {
    var model = DataModelCenter.getDataModel(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME)
        .orElseThrow(() -> new BusinessException("业务 Event 事实模型不可用"));
    var row = model.get(eventId).getOrElseThrow(error -> new BusinessException("业务 Event 原始事实读取失败", error));
    if (row == null) {
      throw new BusinessException("业务 Event 原始事实不存在");
    }
    var values = Json.tryToMap(String.valueOf(row.get("occurrenceJson")))
        .getOrElseThrow(error -> new BusinessException("业务 Event 原始事实无法解析", error));
    var schema = Json.tryToMap(String.valueOf(row.get("schemaJson")))
        .getOrElseThrow(error -> new BusinessException("业务 Event 原始 Schema 无法解析", error));
    var subject = object(values.get("subject"));
    return BusinessEventOccurrence.create(eventId,
        new BusinessEventIdentity(text(values, "modelFullName"), text(values, "eventName")), text(values, "modelRevision"),
        new BusinessEventSubject(text(subject, "modelFullName"), subject.get("id")), schema, object(values.get("payload")),
        Instant.parse(text(values, "occurredAt")), text(values, "producerRef"), text(values, "actorRef"),
        text(values, "correlationId"), text(values, "causationId"), text(values, "executionId"))
        .withRequireUnfilteredModelRead(Boolean.TRUE.equals(values.get("requireUnfilteredModelRead")));
  }

  private Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new BusinessException("业务 Event 事实字段必须为对象");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
    return result;
  }

  private String text(Map<String, Object> values, String field) {
    return values.get(field) == null ? null : String.valueOf(values.get(field));
  }

  private <T> T inNewTransaction(Function<DataModelWriteGuard, T> operation) {
    TransactionTemplate transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return BusinessModelPublication.withPublishedContext(() -> BusinessActionExecutionScope.detached(() -> DataModelExecutionContext.withPlatformWrite(() ->
        transaction.execute(status -> {
          var guard = BusinessTransactionResources.capture(manager, status);
          return DataModelExecutionContext.withWriteGuard(guard, () -> operation.apply(guard));
        }))));
  }

  private DataModel deliveries() {
    return DataModelCenter.getDataModel(MODEL_FULL_NAME)
        .orElseThrow(() -> new BusinessException("业务 Event 投递模型不可用"));
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
  }
}
