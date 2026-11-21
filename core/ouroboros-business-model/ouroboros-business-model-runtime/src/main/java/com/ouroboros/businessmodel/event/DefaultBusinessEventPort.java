package com.ouroboros.businessmodel.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.action.BusinessActionEventContext;
import com.ouroboros.businessmodel.action.BusinessActionExecutionScope;
import com.ouroboros.businessmodel.annotation.BusinessEventPayload;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.businessmodel.transaction.BusinessTransactionResources;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonSchemas;

/** Validates a business fact and records it in the current writable transaction. */
public class DefaultBusinessEventPort implements BusinessEventPort {
  private final BusinessModelBehaviorProvider behaviorProvider;
  private final Supplier<List<BusinessEventProducer>> producers;
  private final BusinessEventOccurrenceRecorder recorder;
  private final PlatformTransactionManager transactionManager;

  public DefaultBusinessEventPort(BusinessModelBehaviorProvider behaviorProvider,
                                  Supplier<List<BusinessEventProducer>> producers,
                                  BusinessEventOccurrenceRecorder recorder,
                                  PlatformTransactionManager transactionManager) {
    this.behaviorProvider = Objects.requireNonNull(behaviorProvider);
    this.producers = Objects.requireNonNull(producers);
    this.recorder = Objects.requireNonNull(recorder);
    this.transactionManager = Objects.requireNonNull(transactionManager);
  }

  @Override
  public <T> T inTransaction(Supplier<T> producer) {
    Objects.requireNonNull(producer);
    return BusinessModelPublication.withPublishedContext(() -> new TransactionTemplate(transactionManager).execute(status -> DataModelExecutionContext.withWriteGuard(
        BusinessTransactionResources.capture(transactionManager, status), () -> {
          T result = producer.get();
          if (status.isRollbackOnly()) {
            throw new BusinessException("业务 Event 源事务已标记回滚");
          }
          return result;
        })));
  }

  @Override
  public BusinessEventOccurrence<Map<String, Object>> emit(BusinessEventIdentity identity,
                                                           BusinessEventSubject subject,
                                                           Object payload,
                                                           BusinessEventEmissionContext context) {
    try {
      return BusinessModelPublication.withPublishedContext(() -> emitInSourceTransaction(identity, subject, payload, context));
    } catch (RuntimeException failure) {
      DataModelExecutionContext.markRollbackOnly();
      throw failure;
    }
  }

  private BusinessEventOccurrence<Map<String, Object>> emitInSourceTransaction(BusinessEventIdentity identity,
                                                                               BusinessEventSubject subject,
                                                                               Object payload,
                                                                               BusinessEventEmissionContext context) {
    Objects.requireNonNull(identity, "Event identity must not be null");
    Objects.requireNonNull(subject, "Event subject must not be null");
    Objects.requireNonNull(context, "Event emission context must not be null");
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        || DataModelExecutionContext.isReadOnly()) {
      throw new BusinessException("业务 Event 必须在可写的实际事务内产生");
    }
    BusinessModelBehavior behavior = behaviorProvider.getBusinessModelBehavior(identity.modelFullName())
        .orElseThrow(() -> new BusinessException("业务 Event 模型未发布"));
    EventDefinition definition = behavior.getEvents().get(identity.eventName());
    if (definition == null) {
      throw new BusinessException("业务 Event 未发布");
    }
    BusinessEventIdentity publishedIdentity = new BusinessEventIdentity(behavior.getModelFullName(), definition.getName());
    String producerRef = authorizeProducer(publishedIdentity, definition, context);
    BusinessEventSubject publishedSubject = resolveSubject(publishedIdentity, definition, subject);
    BusinessEventPayload typedPayload = payload == null ? null : payload.getClass().getAnnotation(BusinessEventPayload.class);
    if (typedPayload != null && (!typedPayload.modelFullName().equalsIgnoreCase(publishedIdentity.modelFullName())
        || !typedPayload.eventName().equalsIgnoreCase(publishedIdentity.eventName()))) {
      throw new BusinessException("业务 Event 载荷类型与事件身份不匹配");
    }
    String currentFingerprint = com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.schemaFingerprint(definition.getPayloadSchema());
    if (context.expectedSchemaFingerprint() != null && !context.expectedSchemaFingerprint().equals(currentFingerprint)
        || typedPayload != null && !typedPayload.schemaFingerprint().isBlank()
        && !typedPayload.schemaFingerprint().equals(
            currentFingerprint)) {
      throw new BusinessException("业务 Event SDK 契约已变化，请重新生成载荷类型");
    }
    String payloadJson = Json.tryToJsonString(payload).getOrElseThrow(error ->
        new BusinessException("业务 Event 载荷无法序列化", error));
    Map<String, Object> fields = Json.tryToMap(payloadJson).getOrElseThrow(error ->
        new BusinessException("业务 Event 载荷必须为 JSON 对象", error));
    if (fields == null || !JsonSchemas.validate(definition.getPayloadSchema(), fields)) {
      throw new BusinessException("业务 Event 载荷不符合契约");
    }
    BusinessActionEventContext action = BusinessActionExecutionScope.currentEventContext().orElse(null);
    String actorRef = CurrentSecurityContextHolder.findAuthentication().map(auth -> auth.getPrincipal())
        .orElseGet(() -> CurrentSecurityContextHolder.getRequiredIdentity().getPrincipal());
    var cause = BusinessEventDeliveryScope.current().orElse(null);
    String correlationId = context.correlationId() != null ? context.correlationId()
        : cause == null ? null : cause.correlationId() == null ? cause.eventId() : cause.correlationId();
    String causationId = context.causationId() != null ? context.causationId() : cause == null ? null : cause.eventId();
    BusinessEventOccurrence<Map<String, Object>> occurrence = BusinessEventOccurrence.create(
        UUID.randomUUID().toString(), publishedIdentity, behavior.getModelRevision(), publishedSubject,
        definition.getPayloadSchema(), fields, context.occurredAt() == null ? Instant.now() : context.occurredAt(), producerRef, actorRef,
        correlationId, causationId, action == null ? null : action.executionId())
        .withRequireUnfilteredModelRead(definition.isRequireUnfilteredModelRead());
    var writeGuard = DataModelExecutionContext.getWriteGuard();
    if (writeGuard == null) {
      throw new BusinessException("业务 Event 源写入必须通过 Action 或 EventPort.inTransaction 执行");
    }
    DataModelExecutionContext.withPlatformWrite(() -> {
      DataModelExecutionContext.withWriteGuard(writeGuard, () -> {
        recorder.record(occurrence);
        return null;
      });
      return null;
    });
    BusinessActionExecutionScope.recordEventId(occurrence.eventId());
    return occurrence;
  }

  private String authorizeProducer(BusinessEventIdentity identity, EventDefinition definition,
                                   BusinessEventEmissionContext context) {
    BusinessActionEventContext action = BusinessActionExecutionScope.currentEventContext().orElse(null);
    if (action != null) {
      if (context.producerRef() != null || action.readOnly() || !action.allows(identity)
          || definition.getAllowedProducerKinds().stream().noneMatch("action"::equalsIgnoreCase)) {
        throw new BusinessException("当前业务 Action 不允许产生此 Event");
      }
      return action.modelFullName() + "#action:" + action.actionName();
    }
    if (context.producerRef() == null || context.producerRef().isBlank()) {
      throw new BusinessException("非 Action 生产者必须声明已注册的 producerRef");
    }
    List<BusinessEventProducer> matches = producers.get().stream()
        .filter(producer -> context.producerRef().equalsIgnoreCase(producer.getName())).toList();
    if (matches.size() != 1 || "action".equalsIgnoreCase(matches.get(0).getKind())
        || definition.getAllowedProducerKinds().stream().noneMatch(matches.get(0).getKind()::equalsIgnoreCase)
        || !matches.get(0).isEmissionAllowed(identity, context)
        || !BusinessBehaviorSecurity.hasAuthority(BusinessEventAuthorities.emit(identity))) {
      throw new BusinessException("业务 Event 生产者未注册或未授权");
    }
    return matches.get(0).getName();
  }

  private BusinessEventSubject resolveSubject(BusinessEventIdentity identity, EventDefinition definition,
                                              BusinessEventSubject subject) {
    if (!identity.modelFullName().equalsIgnoreCase(subject.modelFullName())
        || subject.isInstance() != "instance".equalsIgnoreCase(definition.getSubject())) {
      throw new BusinessException("业务 Event 主体不符合契约");
    }
    if (!subject.isInstance()) {
      return BusinessEventSubject.model(identity.modelFullName());
    }
    var model = DataModelCenter.getDataModel(identity.modelFullName())
        .orElseThrow(() -> new BusinessException("业务 Event 对象模型不可用"));
    if (model.getPrimaryKeys().size() != 1 || !"id".equalsIgnoreCase(model.getPrimaryKeys().get(0).getName())) {
      throw new BusinessException("业务 Event 对象必须声明唯一的 id 主键");
    }
    try {
      return BusinessEventSubject.instance(identity.modelFullName(), model.getPrimaryKeys().get(0)
          .getValueType().convert(subject.id()));
    } catch (RuntimeException error) {
      throw new BusinessException("业务 Event 对象标识不符合契约", error);
    }
  }
}
