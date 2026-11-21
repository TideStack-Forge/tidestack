package com.ouroboros.businessmodel.event;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.BusinessBindingCapabilities;
import com.ouroboros.businessmodel.behavior.BusinessObjectReferenceSchemas;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.businessmodel.transaction.BusinessTransactionResources;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.util.JsonSchemas;

/** Resolves current consumer identity before exposing a complete immutable Event to its target. */
public class DefaultBusinessEventSubscriptionRuntime implements BusinessEventSubscriptionRuntime {
  private final BusinessModelBehaviorProvider behaviorProvider;
  private final BusinessActionRuntime actions;
  private final Supplier<List<BusinessEventListener>> listeners;
  private final BusinessEventConsumerIdentityResolver identities;
  private final PlatformTransactionManager transactionManager;
  private final BusinessEventDisclosure disclosure;

  public DefaultBusinessEventSubscriptionRuntime(BusinessModelBehaviorProvider behaviorProvider,
                                                 BusinessActionRuntime actions,
                                                 Supplier<List<BusinessEventListener>> listeners,
                                                 BusinessEventConsumerIdentityResolver identities,
                                                 PlatformTransactionManager transactionManager) {
    this(behaviorProvider, actions, listeners, identities, transactionManager, new DefaultBusinessEventDisclosure(behaviorProvider));
  }

  public DefaultBusinessEventSubscriptionRuntime(BusinessModelBehaviorProvider behaviorProvider,
      BusinessActionRuntime actions, Supplier<List<BusinessEventListener>> listeners, BusinessEventConsumerIdentityResolver identities,
      PlatformTransactionManager transactionManager, BusinessEventDisclosure disclosure) {
    this.disclosure = Objects.requireNonNull(disclosure);
    this.behaviorProvider = Objects.requireNonNull(behaviorProvider);
    this.actions = Objects.requireNonNull(actions);
    this.listeners = Objects.requireNonNull(listeners);
    this.identities = Objects.requireNonNull(identities);
    this.transactionManager = Objects.requireNonNull(transactionManager);
  }

  @Override
  public java.util.Optional<BusinessEventDeliveryAttempt> inspectAttempt(String consumerModelFullName, String subscriptionName) {
    return BusinessModelPublication.withPublishedContext(() -> behaviorProvider.getBusinessModelBehavior(consumerModelFullName)
        .flatMap(consumer -> java.util.Optional.ofNullable(consumer.getEventSubscriptions().get(subscriptionName))
            .filter(subscription -> !Boolean.FALSE.equals(subscription.getIsEnabled()) && executionCompatible(subscription)).map(subscription -> {
          if (subscription.getTarget().getListenerName() != null && !subscription.getTarget().getListenerName().isBlank()) {
            var matches = listeners.get().stream().filter(listener -> subscription.getTarget().getListenerName().equalsIgnoreCase(listener.getName())).toList();
            var listener = matches.size() == 1 ? matches.get(0) : null;
            return new BusinessEventDeliveryAttempt(consumer.getModelRevision(), null,
                listener == null ? null : "listener:" + subscription.getTarget().getListenerName() + ":" + listener.getImplementationRef(),
                listener == null ? null : listener.getImplementationVersion(), subscription.getExecutionMode(), resolveUser(consumerModelFullName).orElse(null));
          }
          var implementation = actions.inspectImplementation(subscription.getTarget().getModelFullName(), subscription.getTarget().getActionName());
          return new BusinessEventDeliveryAttempt(consumer.getModelRevision(),
              implementation.map(com.ouroboros.businessmodel.action.BusinessActionImplementation::modelRevision).orElse(null),
              implementation.map(com.ouroboros.businessmodel.action.BusinessActionImplementation::implementationRef).orElse(null),
              implementation.map(com.ouroboros.businessmodel.action.BusinessActionImplementation::implementationVersion).orElse(null), subscription.getExecutionMode(), resolveUser(consumerModelFullName).orElse(null));
        })));
  }

  @Override
  public BusinessEventDeliveryResult deliver(BusinessEventOccurrence<?> occurrence,
                                              String consumerModelFullName, String subscriptionName,
                                              String deliveryId, int attempt) {
    return BusinessModelPublication.withPublishedContext(() ->
        deliverInPublishedContext(occurrence, consumerModelFullName, subscriptionName, deliveryId, attempt, null));
  }

  @Override
  public BusinessEventDeliveryResult deliver(BusinessEventOccurrence<?> occurrence, String consumerModelFullName,
      String subscriptionName, String deliveryId, int attempt, BusinessEventDeliveryAttempt expected) {
    return BusinessModelPublication.withPublishedContext(() -> {
      if (!Objects.equals(expected, inspectAttempt(consumerModelFullName, subscriptionName).orElse(null))) {
        return BusinessEventDeliveryResult.contractBlocked("subscription-attempt-contract-changed");
      }
      return deliverInPublishedContext(occurrence, consumerModelFullName, subscriptionName, deliveryId, attempt, expected);
    });
  }

  @EventListener
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void validatePublishedBindings(OuroborosContextRefreshedEvent event) {
    var available = listeners.get();
    java.util.List<String> errors = new java.util.ArrayList<>();
    behaviorProvider.getBusinessModelBehaviorMap().values().forEach(model ->
        model.getEventSubscriptions().values().forEach(subscription -> {
          if (Boolean.FALSE.equals(subscription.getIsEnabled())) { return; }
          if (subscription.getSourceEvent() == null || subscription.getTarget() == null) {
            errors.add(model.getModelFullName() + ".eventSubscriptions." + subscription.getName() + ".contract-incomplete");
            return;
          }
          var executor = identities.resolve(model.getModelFullName());
          if (executor.isEmpty()) {
            errors.add(model.getModelFullName() + ".consumer-identity-unavailable");
          } else if (subscription.getSourceEvent() != null) {
            boolean authorized = CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(executor.get()), () -> {
              if (!BusinessBehaviorSecurity.hasAuthority(BusinessEventAuthorities.read(new BusinessEventIdentity(
                  subscription.getSourceEvent().getModelFullName(), subscription.getSourceEvent().getEventName())))) { return false; }
              if (subscription.getTarget() == null || !"action".equals(subscription.getTarget().getKind())) { return true; }
              var target = behaviorProvider.getBusinessModelBehavior(subscription.getTarget().getModelFullName()).orElse(null);
              return target != null && BusinessBehaviorSecurity.hasAuthority(target.executionAuthorityName(subscription.getTarget().getActionName()));
            });
            if (!authorized) { errors.add(model.getModelFullName() + ".consumer-authority-unavailable"); }
          }
          if (!executionCompatible(subscription)) {
            errors.add(model.getModelFullName() + ".eventSubscriptions." + subscription.getName() + ".executionMode-incompatible");
          }
          if (subscription.getTarget().getListenerName() != null && !subscription.getTarget().getListenerName().isBlank()) {
            var matches = available.stream().filter(listener ->
                subscription.getTarget().getListenerName().equalsIgnoreCase(listener.getName())).toList();
            if (matches.size() != 1 || !BusinessBindingCapabilities.isSynchronous(matches.get(0))) {
              errors.add(model.getModelFullName() + ".eventSubscriptions." + subscription.getName()
                  + ".listenerName-unavailable");
            } else if (!listenerConfigurationValid(matches.getFirst(), subscription)) {
              errors.add(model.getModelFullName() + ".eventSubscriptions." + subscription.getName() + ".listenerConfig-invalid");
            }
            if (matches.size() == 1 && !listenerInputDefinitionValid(matches.getFirst(), subscription)) {
              errors.add(model.getModelFullName() + ".eventSubscriptions." + subscription.getName() + ".listenerInputMapping-invalid");
            }
          } else if (subscription.getTarget().getConfig() != null) {
            errors.add(model.getModelFullName() + ".eventSubscriptions." + subscription.getName() + ".listenerConfig-requires-listener");
          }
        }));
    if (!errors.isEmpty()) {
      throw new BusinessException("业务 Event 监听绑定验证失败：" + String.join("; ", errors));
    }
  }

  private BusinessEventDeliveryResult deliverInPublishedContext(BusinessEventOccurrence<?> occurrence,
                                                                 String consumerModelFullName, String subscriptionName,
                                                                 String deliveryId, int attempt, BusinessEventDeliveryAttempt expectedAttempt) {
    Objects.requireNonNull(occurrence);
    var consumer = behaviorProvider.getBusinessModelBehavior(consumerModelFullName).orElse(null);
    var source = behaviorProvider.getBusinessModelBehavior(occurrence.identity().modelFullName()).orElse(null);
    var subscription = consumer == null ? null : consumer.getEventSubscriptions().get(subscriptionName);
    var currentEvent = source == null ? null : source.getEvents().get(occurrence.identity().eventName());
    if (subscription != null && Boolean.FALSE.equals(subscription.getIsEnabled())) {
      return BusinessEventDeliveryResult.contractBlocked("subscription-disabled");
    }
    if (subscription == null || subscription.getSourceEvent() == null || subscription.getTarget() == null || currentEvent == null
        || !occurrence.identity().modelFullName().equalsIgnoreCase(subscription.getSourceEvent().getModelFullName())
        || !occurrence.identity().eventName().equalsIgnoreCase(subscription.getSourceEvent().getEventName())
        || !JsonSchemas.validate(currentEvent.getPayloadSchema(), occurrence.payloadFields())) {
      return BusinessEventDeliveryResult.contractBlocked("subscription-contract-unavailable");
    }
    if (!BusinessObjectReferenceSchemas.unsupportedReferencePaths(occurrence.payloadSchema()).isEmpty()
        || !BusinessObjectReferenceSchemas.unsupportedReferencePaths(currentEvent.getPayloadSchema()).isEmpty()) {
      return BusinessEventDeliveryResult.contractBlocked("event-object-reference-schema-unavailable");
    }
    if ("listener".equals(subscription.getTarget().getKind()) && listeners.get().stream().filter(listener ->
        subscription.getTarget().getListenerName() != null && subscription.getTarget().getListenerName().equalsIgnoreCase(listener.getName())).count() != 1) {
      return BusinessEventDeliveryResult.contractBlocked("listener-unavailable");
    }
    if (!executionCompatible(subscription)) {
      return BusinessEventDeliveryResult.contractBlocked(expectedAttempt == null
          ? "subscription-execution-mode-incompatible" : "subscription-attempt-contract-changed");
    }
    if (subscription.getExecutionMode() == BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION
        && (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            || DataModelExecutionContext.getWriteGuard() == null)) {
      return BusinessEventDeliveryResult.contractBlocked("source-transaction-unavailable");
    }
    var user = identities.resolve(consumerModelFullName);
    if (user.isEmpty()) {
      return BusinessEventDeliveryResult.authorizationBlocked("consumer-identity-unavailable");
    }
    return BusinessEventDeliveryScope.with(occurrence, () ->
        com.ouroboros.businessmodel.action.BusinessActionExecutionScope.detached(() ->
        CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user.get()), () -> {
      if (subscription.getExecutionMode() == BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
          return BusinessEventDeliveryResult.contractBlocked("independent-listener-requires-transaction-free-entry");
        }
        return DataModelExecutionContext.withReadOnly(true, () -> deliverAuthorized(occurrence,
            consumer.getModelFullName(), subscription, currentEvent.getPayloadSchema(),
            new BusinessEventDeliveryContext(deliveryId, consumer.getModelFullName(), subscription.getName(),
                user.get().getUserIdentity(), attempt, occurrence.correlationId(), occurrence.eventId(), subscription.getTarget().getConfig()), expectedAttempt));
      }
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
      return transaction.execute(status -> DataModelExecutionContext.withWriteGuard(
          BusinessTransactionResources.capture(transactionManager, status), () -> {
            var result = deliverAuthorized(occurrence, consumer.getModelFullName(), subscription,
                currentEvent.getPayloadSchema(),
                new BusinessEventDeliveryContext(deliveryId, consumer.getModelFullName(), subscription.getName(),
                    user.get().getUserIdentity(), attempt, occurrence.correlationId(), occurrence.eventId(), subscription.getTarget().getConfig()), expectedAttempt);
            if (status.isRollbackOnly()) {
              throw new BusinessException("业务 Event 消费事务已标记回滚");
            }
            return result;
          }));
    })));
  }

  private BusinessEventDeliveryResult deliverAuthorized(BusinessEventOccurrence<?> occurrence,
                                                        String consumerModelFullName,
                                                        EventSubscriptionDefinition subscription,
                                                        Map<String, Object> currentPayloadSchema,
                                                        BusinessEventDeliveryContext delivery, BusinessEventDeliveryAttempt expectedAttempt) {
    if (!disclosure.canRead(occurrence)) {
      return BusinessEventDeliveryResult.authorizationBlocked("event-disclosure-denied");
    }
    Map<String, Object> context = Map.of("event", occurrence.toMap());
    if (subscription.getTarget().getListenerName() != null && !subscription.getTarget().getListenerName().isBlank()) {
      List<BusinessEventListener> matches = listeners.get().stream().filter(listener ->
          subscription.getTarget().getListenerName().equalsIgnoreCase(listener.getName())).toList();
      if (matches.size() != 1 || !BusinessBindingCapabilities.isSynchronous(matches.get(0))) {
        return BusinessEventDeliveryResult.contractBlocked("listener-unavailable");
      }
      if (!listenerConfigurationValid(matches.getFirst(), subscription)
          || !listenerInputDefinitionValid(matches.getFirst(), subscription)) {
        return BusinessEventDeliveryResult.contractBlocked("listener-config-incompatible");
      }
      var selected = matches.getFirst();
      if (!supports(selected, subscription.getExecutionMode())) {
        return BusinessEventDeliveryResult.contractBlocked("subscription-attempt-contract-changed");
      }
      var selectedAttempt = new BusinessEventDeliveryAttempt(behaviorProvider.getBusinessModelBehavior(consumerModelFullName).orElseThrow().getModelRevision(),
          null, "listener:" + subscription.getTarget().getListenerName() + ":" + selected.getImplementationRef(), selected.getImplementationVersion(), subscription.getExecutionMode(), resolveUser(consumerModelFullName).orElse(null));
      if (expectedAttempt != null && !expectedAttempt.equals(selectedAttempt)) {
        return BusinessEventDeliveryResult.contractBlocked("subscription-attempt-contract-changed");
      }
      String expected = selected.getPayloadSchemaFingerprint();
      if (expected != null && (!expected.equals(
          com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.schemaFingerprint(currentPayloadSchema))
          || !expected.equals(com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.schemaFingerprint(occurrence.payloadSchema())))) {
        return BusinessEventDeliveryResult.contractBlocked("listener-sdk-contract-incompatible");
      }
      Map<String, Object> input;
      try {
        var plan = behaviorProvider.getBusinessModelBehavior(consumerModelFullName).orElseThrow()
            .subscriptionInputPlan(subscription.getName());
        input = plan == null ? Map.of() : plan.map(context, java.util.function.UnaryOperator.identity());
        // Materialize exactly once before Schema validation; evaluation failure is distinct from a value/type mismatch.
        delivery = new BusinessEventDeliveryContext(delivery.deliveryId(), delivery.consumerModelFullName(), delivery.subscriptionName(),
            delivery.user(), delivery.attempt(), delivery.correlationId(), delivery.causationId(), delivery.listenerConfig(), input);
        var inputSchema = selected.getListenerInputSchema(subscription.getTarget().getConfig());
        if (inputSchema != null && !inputSchema.isEmpty() && !JsonSchemas.validate(inputSchema, delivery.listenerInput())) {
          return BusinessEventDeliveryResult.contractBlocked("listener-input-incompatible");
        }
      } catch (RuntimeException failure) {
        return BusinessEventDeliveryResult.contractBlocked("listener-input-mapping-failed");
      }
      selected.onEvent(occurrence, delivery);
      return BusinessEventDeliveryResult.delivered();
    }
    if (subscription.getTarget().getConfig() != null) {
      return BusinessEventDeliveryResult.contractBlocked("listener-config-requires-listener");
    }
    var targetModel = behaviorProvider.getBusinessModelBehavior(subscription.getTarget().getModelFullName()).orElse(null);
    var action = targetModel == null ? null : targetModel.getActions().get(subscription.getTarget().getActionName());
    if (action == null || !Boolean.TRUE.equals(action.getIsEnabled())) {
      return BusinessEventDeliveryResult.contractBlocked("target-action-unavailable");
    }
    if (com.ouroboros.businessmodel.action.BusinessActionOutputs.isFile(action.getOutputSchema())) {
      return BusinessEventDeliveryResult.contractBlocked("target-action-file-output-not-consumable");
    }
    if (!BusinessBehaviorSecurity.hasAuthority(targetModel.executionAuthorityName(action.getName()))) {
      return BusinessEventDeliveryResult.authorizationBlocked("target-action-denied");
    }
    Map<String, Object> input = behaviorProvider.getBusinessModelBehavior(consumerModelFullName).orElseThrow()
        .subscriptionInputPlan(subscription.getName()).map(context, java.util.function.UnaryOperator.identity());
    BusinessActionInvocation invocation = new BusinessActionInvocation(targetModel.getModelFullName(), action.getName(),
        com.ouroboros.businessmodel.action.BusinessActionInputs.fromPartitions(input));
    var availability = actions.checkAvailability(invocation);
    if (availability.status() == com.ouroboros.businessmodel.action.BusinessActionAvailability.Status.BLOCKED) {
      if (availability.blockage() == com.ouroboros.businessmodel.action.BusinessActionAvailability.Blockage.CONTRACT) {
        return BusinessEventDeliveryResult.contractBlocked("target-action-contract-unavailable");
      }
      if (availability.blockage() == com.ouroboros.businessmodel.action.BusinessActionAvailability.Blockage.PRECONDITION) {
        return BusinessEventDeliveryResult.preconditionBlocked("target-action-precondition-unsatisfied");
      }
      return BusinessEventDeliveryResult.authorizationBlocked("target-object-or-action-denied");
    }
    if (availability.status() == com.ouroboros.businessmodel.action.BusinessActionAvailability.Status.NEEDS_INPUT) {
      return BusinessEventDeliveryResult.contractBlocked("target-action-input-incompatible");
    }
    actions.invoke(invocation);
    return BusinessEventDeliveryResult.delivered();
  }

  @Override
  public java.util.Optional<String> resolveUser(String consumerModelFullName) {
    return identities.resolve(consumerModelFullName).map(com.ouroboros.security.rbac.User::getUserIdentity);
  }

  private boolean executionCompatible(EventSubscriptionDefinition subscription) {
    if (subscription.getExecutionMode() == null || subscription.getTarget() == null) { return false; }
    var target = subscription.getTarget();
    if ("action".equals(target.getKind())) {
      return target.getModelFullName() != null && !target.getModelFullName().isBlank()
          && target.getActionName() != null && !target.getActionName().isBlank()
          && target.getListenerName() == null
          && subscription.getExecutionMode() != BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT;
    }
    if (!"listener".equals(target.getKind())) { return false; }
    var matches = listeners.get().stream().filter(listener -> target.getListenerName() != null
        && target.getListenerName().equalsIgnoreCase(listener.getName())).toList();
    return matches.size() == 1 && supports(matches.getFirst(), subscription.getExecutionMode());
  }

  private boolean supports(BusinessEventListener listener, BusinessEventExecutionMode mode) {
    return mode == BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT
        ? listener.getDeliveryMode() == BusinessEventDeliveryMode.INDEPENDENT
        : listener.getDeliveryMode() == BusinessEventDeliveryMode.ATOMIC;
  }

  private boolean listenerConfigurationValid(BusinessEventListener listener, EventSubscriptionDefinition subscription) {
    Map<String, Object> schema = listener.getListenerConfigSchema();
    Map<String, Object> config = subscription.getTarget().getConfig();
    if (schema == null) { return config == null || config.isEmpty(); }
    return JsonSchemas.isValidSchema(schema) && JsonSchemas.validate(schema, config == null ? Map.of() : config);
  }

  private boolean listenerInputDefinitionValid(BusinessEventListener listener, EventSubscriptionDefinition subscription) {
    Map<String, Object> schema = listener.getListenerInputSchema(subscription.getTarget().getConfig());
    if (schema == null || schema.isEmpty()) { return subscription.getTarget().getInputMapping() == null || subscription.getTarget().getInputMapping().isEmpty(); }
    return "object".equals(schema.get("type")) && JsonSchemas.isValidSchema(schema)
        && (subscription.getTarget().getInputMapping() != null || JsonSchemas.validate(schema, Map.of()));
  }

}
