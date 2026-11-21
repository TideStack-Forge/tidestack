package com.ouroboros.businessmodel.event.store;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime;
import com.ouroboros.businessmodel.event.BusinessEventExecutionMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessEventOccurrenceRecorder;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionMapping;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Uses the official DataModel insertion path in the source transaction. */
public class DataModelBusinessEventOccurrenceRecorder implements BusinessEventOccurrenceRecorder {
  public static final String MODEL_FULL_NAME = "ouroboros.BusinessEventOccurrence";
  private static final Logger logger = LoggerFactory.getLogger(DataModelBusinessEventOccurrenceRecorder.class);
  private final BusinessModelBehaviorProvider behaviors;
  private final Consumer<String> wakeup;
  private final Supplier<BusinessEventSubscriptionRuntime> runtime;
  private static final ThreadLocal<java.util.Deque<String>> PROPAGATION = ThreadLocal.withInitial(java.util.ArrayDeque::new);
  private static final int MAX_PROPAGATION_DEPTH = 32;

  public DataModelBusinessEventOccurrenceRecorder(BusinessModelBehaviorProvider behaviors, Consumer<String> wakeup) {
    this(behaviors, () -> null, wakeup);
  }

  public DataModelBusinessEventOccurrenceRecorder(BusinessModelBehaviorProvider behaviors,
      Supplier<BusinessEventSubscriptionRuntime> runtime, Consumer<String> wakeup) {
    this.runtime = Objects.requireNonNull(runtime);
    this.behaviors = Objects.requireNonNull(behaviors);
    this.wakeup = Objects.requireNonNull(wakeup);
  }

  @Override
  public void record(BusinessEventOccurrence<?> occurrence) {
    var path = PROPAGATION.get();
    boolean entered = false;
    try {
      if (path.size() >= MAX_PROPAGATION_DEPTH) {
        throw new BusinessException("业务 Event 同步传播超过深度限制：" + path + " -> " + occurrence.identity());
      }
      path.addLast(occurrence.identity().toString());
      entered = true;
      recordInSourceTransaction(occurrence);
    } catch (RuntimeException failure) {
      DataModelExecutionContext.markRollbackOnly();
      throw failure;
    } finally {
      if (entered) { path.removeLast(); }
      if (path.isEmpty()) { PROPAGATION.remove(); }
    }
  }

  private void recordInSourceTransaction(BusinessEventOccurrence<?> occurrence) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        || DataModelExecutionContext.getWriteGuard() == null) {
      throw new BusinessException("业务 Event 事实必须在经过资源验证的可写源事务内记录");
    }
    var model = DataModelCenter.getDataModel(MODEL_FULL_NAME)
        .orElseThrow(() -> new BusinessException("业务 Event 事实模型不可用"));
    DataModelExecutionContext.beforeWrite(model);
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", occurrence.eventId());
    values.put("modelFullName", occurrence.identity().modelFullName());
    values.put("eventName", occurrence.identity().eventName());
    values.put("modelRevision", occurrence.modelRevision());
    values.put("occurrenceJson", Json.tryToJsonString(occurrence.toMap()).getOrElseThrow(error ->
        new BusinessException("业务 Event 事实无法序列化", error)));
    values.put("schemaJson", Json.tryToJsonString(occurrence.payloadSchema()).getOrElseThrow(error ->
        new BusinessException("业务 Event Schema 无法序列化", error)));
    values.put("occurredAt", LocalDateTime.ofInstant(occurrence.occurredAt(), ZoneOffset.UTC));
    var existing = model.get(occurrence.eventId()).getOrElseThrow(error -> new BusinessException("业务 Event 事实查重失败", error));
    if (existing != null) {
      if (!Json.toMap(String.valueOf(existing.get("occurrenceJson"))).equals(Json.toMap(String.valueOf(values.get("occurrenceJson"))))
          || !Json.toMap(String.valueOf(existing.get("schemaJson"))).equals(Json.toMap(String.valueOf(values.get("schemaJson"))))) {
        throw new BusinessException("业务 Event 事实身份冲突");
      }
      return;
    }
    model.insert(values).getOrElseThrow(error -> new BusinessException("业务 Event 事实写入失败", error));
    var published = behaviors.getBusinessModelBehaviorMap();
    for (var consumer : published.values()) {
      for (var subscription : consumer.getEventSubscriptions().values()) {
        if (Boolean.FALSE.equals(subscription.getIsEnabled())
            || !occurrence.identity().modelFullName().equalsIgnoreCase(subscription.getSourceEvent().getModelFullName())
            || !occurrence.identity().eventName().equalsIgnoreCase(subscription.getSourceEvent().getEventName())
            || !BusinessEventSubscriptionMapping.matches(subscription.getFilter(), occurrence)) {
          continue;
        }
        var deliveryModel = DataModelCenter.getDataModel(BusinessEventOutbox.MODEL_FULL_NAME)
            .orElseThrow(() -> new BusinessException("业务 Event 投递模型不可用"));
        DataModelExecutionContext.beforeWrite(deliveryModel);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("id", deliveryId(occurrence.eventId(), consumer.getModelFullName(), subscription.getName()));
        delivery.put("eventId", occurrence.eventId());
        delivery.put("consumerModelFullName", consumer.getModelFullName());
        delivery.put("subscriptionName", subscription.getName());
        delivery.put("matchedRevision", consumer.getModelRevision());
        var mode = Objects.requireNonNull(subscription.getExecutionMode(), "Subscription executionMode is required");
        delivery.put("executionMode", mode.name());
        delivery.put("status", "PENDING");
        delivery.put("attempt", 0);
        delivery.put("retryCount", 0);
        delivery.put("createdAt", now);
        delivery.put("updatedAt", now);
        deliveryModel.insert(delivery).getOrElseThrow(error -> new BusinessException("业务 Event 逐订阅入队失败", error));
        if (mode == BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION) {
          var consumerRuntime = runtime.get();
          if (consumerRuntime == null) { throw new BusinessException("业务 Event 同步消费 Runtime 不可用"); }
          var attempt = consumerRuntime.inspectAttempt(consumer.getModelFullName(), subscription.getName()).orElseThrow(() ->
              new BusinessException("业务 Event 同步订阅不可用"));
          if (attempt.executionMode() != mode) { throw new BusinessException("业务 Event 同步执行模式已改变"); }
          var result = consumerRuntime.deliver(occurrence, consumer.getModelFullName(), subscription.getName(),
              String.valueOf(delivery.get("id")), 1, attempt);
          if (result.status() != com.ouroboros.businessmodel.event.BusinessEventDeliveryResult.Status.DELIVERED) {
            throw new BusinessException("业务 Event 同步消费失败：" + result.diagnosticCode());
          }
          Map<String, Object> completed = new LinkedHashMap<>();
          completed.put("status", "SUCCESS");
          completed.put("attempt", 1);
          completed.put("user", attempt.user());
          completed.put("attemptRevision", attempt.subscriptionRevision());
          completed.put("targetRevision", attempt.targetRevision());
          completed.put("implementationRef", attempt.implementationRef());
          completed.put("implementationVersion", attempt.implementationVersion());
          completed.put("updatedAt", LocalDateTime.now(ZoneOffset.UTC));
          Long affected = deliveryModel.update(delivery.get("id"), completed).getOrElseThrow(error ->
              new BusinessException("业务 Event 同步完成记录失败", error));
          if (!Long.valueOf(1).equals(affected)) { throw new BusinessException("业务 Event 同步完成记录丢失"); }
        }
      }
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          wakeup.accept(occurrence.eventId());
        } catch (RuntimeException error) {
          logger.warn("Business Event wakeup failed; recovery scan retains responsibility: eventId={}", occurrence.eventId(), error);
        }
      }
    });
  }

  public static String deliveryId(String eventId, String consumerModelFullName, String subscriptionName) {
    String key = Json.toJsonString(List.of(eventId, consumerModelFullName.toLowerCase(Locale.ROOT), subscriptionName.toLowerCase(Locale.ROOT)));
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
