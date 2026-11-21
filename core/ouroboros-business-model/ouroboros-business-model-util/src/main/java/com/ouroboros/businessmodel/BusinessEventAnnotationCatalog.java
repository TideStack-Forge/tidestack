package com.ouroboros.businessmodel;

import com.ouroboros.common.app.util.SpringContextUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.businessmodel.annotation.BusinessEvent;
import com.ouroboros.businessmodel.annotation.BusinessEventPayload;
import com.ouroboros.businessmodel.annotation.BusinessEventSubscription;
import com.ouroboros.businessmodel.behavior.BusinessEventSubscriptionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventListener;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Collects Event contract and listener annotations into the normal behavior contribution path. */
@Component
public class BusinessEventAnnotationCatalog implements SmartInitializingSingleton {
  private volatile Snapshot snapshot = Snapshot.empty();

  private final ConfigurableListableBeanFactory beanFactory;

  public BusinessEventAnnotationCatalog(ConfigurableListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<BusinessModelBehaviorContribution> definitions = new ArrayList<>();
    List<BusinessEventSubscriptionBindingContribution> bindings = new ArrayList<>();
    List<BusinessEventListener> listeners = new ArrayList<>();
    for (String beanName : beanFactory.getBeanDefinitionNames()) {
      if (!beanFactory.containsSingleton(beanName)) {
        continue;
      }
      Object bean = beanFactory.getBean(beanName);
      Class<?> targetClass = AopUtils.getTargetClass(bean);
      BusinessEvent event = targetClass.getAnnotation(BusinessEvent.class);
      if (event != null) {
        String source = targetClass.getName();
        definitions.add(new BusinessModelBehaviorContribution(event.modelFullName(), source,
            List.of(), List.of(eventDefinition(event)), List.of(), "2.0"));
      }

      BusinessEventSubscription typeSubscription = targetClass.getAnnotation(BusinessEventSubscription.class);
      if (typeSubscription != null) {
        if (!typeSubscription.listenerInputSchema().isBlank() || !typeSubscription.listenerConfigSchema().isBlank()) {
          throw new IllegalStateException("Only a method listener can declare listenerConfigSchema or listenerInputSchema: " + targetClass.getName());
        }
        definitions.add(typeSubscriptionDefinition(typeSubscription, targetClass.getName(), false));
      }

      for (Method method : targetClass.getMethods()) {
        BusinessEventSubscription subscription = method.getAnnotation(BusinessEventSubscription.class);
        if (subscription == null) {
          continue;
        }
        String source = targetClass.getName() + "#" + method.getName();
        if (subscription.listenerName().isBlank()) {
          throw new IllegalStateException("@BusinessEventSubscription on a method must declare listenerName: " + source);
        }
        Class<?> payloadType = validateListenerMethod(bean, method, source);
        validatePayloadIdentity(payloadType, subscription, source);
        Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
        listeners.add(new AnnotatedListener(subscription.listenerName(), bean, invocableMethod, payloadType, subscription.listenerConfigSchema(), subscription.listenerInputSchema(), subscription.executionMode() == com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT
            ? com.ouroboros.businessmodel.event.BusinessEventDeliveryMode.INDEPENDENT : com.ouroboros.businessmodel.event.BusinessEventDeliveryMode.ATOMIC, source));
        if (hasSourceEvent(subscription)) {
          definitions.add(typeSubscriptionDefinition(subscription, source, true));
        } else {
          if (!subscription.isEnabled() || !subscription.listenerConfig().isBlank() || !subscription.filter().isBlank()
              || !subscription.title().isBlank() || !subscription.description().isBlank()
              || !configurationObject(subscription.inputMapping(), "inputMapping", source).isEmpty()) {
            throw new IllegalStateException("A listener binding cannot replace dynamic subscription configuration or enablement: " + source);
          }
          bindings.add(new BusinessEventSubscriptionBindingContribution(subscription.consumerModelFullName(),
              subscription.subscriptionName(), source, subscription.listenerName(), "2.0"));
        }
      }
    }
    snapshot = new Snapshot(definitions, bindings, listeners);
  }

  public static List<BusinessModelBehaviorContribution> getDefinitionContributions() {
    return currentSnapshot().definitions();
  }

  public static List<BusinessEventSubscriptionBindingContribution> getBindingContributions() {
    return currentSnapshot().bindings();
  }

  public List<BusinessEventListener> getListeners() {
    return snapshot.listeners();
  }

  private static EventDefinition eventDefinition(BusinessEvent annotation) {
    EventDefinition event = new EventDefinition();
    event.setName(annotation.eventName());
    event.setTitle(annotation.title());
    event.setDescription(annotation.description());
    event.setOccurrenceCondition(annotation.occurrenceCondition());
    event.setSubject(annotation.subject());
    event.setPayloadSchema(resolvePayloadSchema(annotation));
    event.setAllowedProducerKinds(List.of(annotation.allowedProducerKinds()));
    event.setRequireUnfilteredModelRead(annotation.requireUnfilteredModelRead());
    return event;
  }

  private static Map<String, Object> resolvePayloadSchema(BusinessEvent annotation) {
    String schema = annotation.payloadSchema().trim();
    if (schema.startsWith("{")) {
      return Json.tryToMap(schema).getOrElseThrow(error ->
          new IllegalStateException("Invalid @BusinessEvent payloadSchema", error));
    }
    try {
      Class<?> payloadType = Class.forName(schema);
      BusinessEventPayload payload = payloadType.getAnnotation(BusinessEventPayload.class);
      if (payload == null || !payload.modelFullName().equalsIgnoreCase(annotation.modelFullName())
          || !payload.eventName().equalsIgnoreCase(annotation.eventName())) {
        throw new IllegalStateException("@BusinessEvent payload type must declare the same @BusinessEventPayload identity: "
            + payloadType.getName());
      }
      return BusinessEventPayloadSchemaGenerator.schema(payloadType);
    } catch (ClassNotFoundException error) {
      throw new IllegalStateException("@BusinessEvent payloadSchema must be JSON Schema or a loadable payload type: "
          + schema, error);
    }
  }

  private static BusinessModelBehaviorContribution typeSubscriptionDefinition(
      BusinessEventSubscription annotation, String source, boolean methodTarget) {
    if (!hasSourceEvent(annotation)) {
      if (methodTarget) {
        throw new IllegalStateException("@BusinessEventSubscription method must declare its source Event: " + source);
      }
      throw new IllegalStateException("@BusinessEventSubscription type must declare its source Event: " + source);
    }
    if (methodTarget && (!annotation.targetActionName().isBlank()
        || !annotation.targetActionModelFullName().isBlank())) {
      throw new IllegalStateException("@BusinessEventSubscription method cannot target an Action: " + source);
    }
    EventSubscriptionDefinition subscription = new EventSubscriptionDefinition();
    subscription.setName(annotation.subscriptionName());
    if (!annotation.isEnabled()) { subscription.setIsEnabled(false); }
    subscription.getSourceEvent().setModelFullName(annotation.sourceEventModelFullName());
    subscription.getSourceEvent().setEventName(annotation.sourceEventName());
    subscription.setFilter(annotation.filter().isBlank() ? null : annotation.filter());
    subscription.getTarget().setModelFullName(annotation.targetActionModelFullName().isBlank() ? null : annotation.targetActionModelFullName());
    subscription.getTarget().setActionName(annotation.targetActionName().isBlank() ? null : annotation.targetActionName());
    subscription.getTarget().setInputMapping(Json.tryToMap(annotation.inputMapping()).getOrElseThrow(error ->
        new IllegalStateException("Invalid @BusinessEventSubscription inputMapping: " + source, error)));
    subscription.getTarget().setListenerName(annotation.listenerName().isBlank() ? null : annotation.listenerName());
    if (!annotation.listenerConfig().isBlank()) {
      if (annotation.listenerName().isBlank() || !annotation.targetActionName().isBlank()
          || !annotation.targetActionModelFullName().isBlank()) {
        throw new IllegalStateException("listenerConfig requires an exclusive listener target: " + source);
      }
      subscription.getTarget().setConfig(configurationObject(annotation.listenerConfig(), "listenerConfig", source));
    }
    subscription.setTitle(annotation.title().isBlank() ? null : annotation.title());
    subscription.setDescription(annotation.description().isBlank() ? null : annotation.description());
    subscription.setExecutionMode(annotation.executionMode());
    subscription.getTarget().setKind(annotation.listenerName().isBlank() ? "action" : "listener");
    return new BusinessModelBehaviorContribution(annotation.consumerModelFullName(), source,
        List.of(), List.of(), List.of(subscription), "2.0");
  }

  private static Map<String, Object> configurationObject(String json, String field, String source) {
    Map<String, Object> value = Json.tryToMap(json).getOrElseThrow(error ->
        new IllegalStateException("Invalid @BusinessEventSubscription " + field + ": " + source, error));
    if (value == null) { throw new IllegalStateException("@BusinessEventSubscription " + field + " must be an object: " + source); }
    return value;
  }

  private static Class<?> validateListenerMethod(Object bean, Method method, String source) {
    if (!BusinessBindingCapabilities.isSynchronous(bean, AopUtils.selectInvocableMethod(method, bean.getClass()))) {
      throw new IllegalStateException("BusinessEventListener must complete synchronously before acknowledgement: " + source);
    }
    Class<?>[] parameters = method.getParameterTypes();
    Type[] genericParameters = method.getGenericParameterTypes();
    if (method.getReturnType() != void.class || parameters.length != 2 || parameters[0] != BusinessEventOccurrence.class
        || parameters[1] != BusinessEventDeliveryContext.class
        || !(genericParameters[0] instanceof ParameterizedType occurrenceType)
        || occurrenceType.getActualTypeArguments().length != 1) {
      throw new IllegalStateException("BusinessEventListener method must accept "
          + "(BusinessEventOccurrence<Payload>, BusinessEventDeliveryContext): " + source);
    }
    Type payloadType = occurrenceType.getActualTypeArguments()[0];
    if (payloadType instanceof ParameterizedType parameterizedType) {
      payloadType = parameterizedType.getRawType();
    }
    if (!(payloadType instanceof Class<?> resolvedType)) {
      throw new IllegalStateException("BusinessEventListener payload type must be a concrete class: " + source);
    }
    return resolvedType;
  }

  private static void validatePayloadIdentity(Class<?> payloadType,
                                             BusinessEventSubscription subscription,
                                             String source) {
    BusinessEventPayload payload = payloadType.getAnnotation(BusinessEventPayload.class);
    if (payload == null || !hasSourceEvent(subscription)) {
      return;
    }
    if (!payload.modelFullName().equalsIgnoreCase(subscription.sourceEventModelFullName())
        || !payload.eventName().equalsIgnoreCase(subscription.sourceEventName())) {
      throw new IllegalStateException("@BusinessEventPayload identity does not match subscription source Event: " + source);
    }
  }

  private static boolean hasSourceEvent(BusinessEventSubscription annotation) {
    if (annotation.sourceEventModelFullName().isBlank() != annotation.sourceEventName().isBlank()) {
      throw new IllegalStateException("@BusinessEventSubscription source Event identity must be complete");
    }
    return !annotation.sourceEventModelFullName().isBlank() && !annotation.sourceEventName().isBlank();
  }

  private static Snapshot currentSnapshot() {
    var context = SpringContextUtil.getApplicationContext();
    if (context == null) {
      return Snapshot.empty();
    }
    var catalog = context.getBeanProvider(BusinessEventAnnotationCatalog.class).getIfAvailable();
    return catalog == null ? Snapshot.empty() : catalog.snapshot;
  }

  private record Snapshot(List<BusinessModelBehaviorContribution> definitions,
                          List<BusinessEventSubscriptionBindingContribution> bindings,
                          List<BusinessEventListener> listeners) {
    private Snapshot {
      definitions = List.copyOf(definitions);
      bindings = List.copyOf(bindings);
      listeners = List.copyOf(listeners);
    }

    private static Snapshot empty() {
      return new Snapshot(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
  }

  private static final class AnnotatedListener implements BusinessEventListener {
    private final String name;
    private final Object bean;
    private final Method method;
    private final Class<?> payloadType;
    private final Map<String, Object> listenerConfigSchema;
    private final Map<String, Object> listenerInputSchema;
    private final com.ouroboros.businessmodel.event.BusinessEventDeliveryMode deliveryMode;

    private AnnotatedListener(String name, Object bean, Method method, Class<?> payloadType, String schema, String inputSchema, com.ouroboros.businessmodel.event.BusinessEventDeliveryMode deliveryMode, String source) {
      this.name = name;
      this.bean = bean;
      this.method = method;
      this.payloadType = payloadType;
      this.deliveryMode = deliveryMode;
      this.listenerConfigSchema = schema.isBlank() ? null : com.ouroboros.businessmodel.action.BusinessActionInputValues.snapshotMap(
          configurationObject(schema, "listenerConfigSchema", source));
      this.listenerInputSchema = inputSchema.isBlank() ? null : com.ouroboros.businessmodel.action.BusinessActionInputValues.snapshotMap(
          configurationObject(inputSchema, "listenerInputSchema", source));
      if (listenerInputSchema != null && (!"object".equals(listenerInputSchema.get("type"))
          || !com.ouroboros.util.JsonSchemas.isValidSchema(listenerInputSchema))) {
        throw new IllegalStateException("Invalid @BusinessEventSubscription listenerInputSchema: " + source);
      }
      if (listenerConfigSchema != null && !com.ouroboros.util.JsonSchemas.isValidSchema(listenerConfigSchema)) {
        throw new IllegalStateException("Invalid @BusinessEventSubscription listenerConfigSchema: " + source);
      }
    }

    @Override
    public Map<String, Object> getListenerConfigSchema() { return listenerConfigSchema; }

    @Override
    public Map<String, Object> getListenerInputSchema() { return listenerInputSchema; }

    @Override
    public com.ouroboros.businessmodel.event.BusinessEventDeliveryMode getDeliveryMode() { return deliveryMode; }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getPayloadSchemaFingerprint() {
      var payload = payloadType.getAnnotation(BusinessEventPayload.class);
      return payload == null || payload.schemaFingerprint().isBlank() ? null : payload.schemaFingerprint();
    }

    @Override
    public String getImplementationRef() {
      return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    @Override
    public String getImplementationVersion() {
      return method.getDeclaringClass().getPackage().getImplementationVersion();
    }

    @Override
    public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
      BusinessEventOccurrence<?> typedOccurrence = occurrence.withPayloadProjection(payload ->
          Json.tryToBean(payloadType, Json.toJsonString(payload)).getOrElseThrow(error ->
              new BusinessException("业务 Event 载荷无法转换为监听器类型", error)));
      BusinessEventPayload payload = payloadType.getAnnotation(BusinessEventPayload.class);
      if (payload != null && (!payload.modelFullName().equalsIgnoreCase(occurrence.identity().modelFullName())
          || !payload.eventName().equalsIgnoreCase(occurrence.identity().eventName()))) {
        throw new BusinessException("业务 Event 载荷类型与事件身份不匹配");
      }
      try {
        method.invoke(bean, typedOccurrence, delivery);
      } catch (IllegalAccessException error) {
        throw new BusinessException("业务 Event 监听器不可访问", error);
      } catch (InvocationTargetException error) {
        Throwable cause = error.getCause();
        if (cause instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        throw new BusinessException("业务 Event 监听器执行失败", cause);
      }
    }
  }
}
