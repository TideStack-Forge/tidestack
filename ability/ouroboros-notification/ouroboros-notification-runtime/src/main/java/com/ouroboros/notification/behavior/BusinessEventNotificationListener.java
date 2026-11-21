package com.ouroboros.notification.behavior;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryMode;
import com.ouroboros.businessmodel.event.BusinessEventDisclosure;
import com.ouroboros.businessmodel.event.BusinessEventListener;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.DispatchOptions;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.NotificationDeliveryPolicy;
import com.ouroboros.notification.NotificationDispatchRequest;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonSchemas;

/** One configured listener for dynamic Event notifications, delivered through the existing recoverable dispatcher. */
public final class BusinessEventNotificationListener implements BusinessEventListener {
  public static final String NAME = BusinessWorkflowNotificationSource.LISTENER_NAME;
  private static final Map<String, Object> GENERIC_CONFIG_SCHEMA = Map.of("type", "object", "required", List.of("template"),
      "additionalProperties", false, "properties", Map.of("template", Map.of("type", "string", "minLength", 1),
          "options", Map.of("type", "object", "additionalProperties", false, "properties", Map.of(
              "channels", Map.of("type", "array", "items", Map.of("type", "string", "minLength", 1), "minItems", 1, "uniqueItems", true),
              "channelOptions", Map.of("type", "object", "additionalProperties", Map.of("type", "object")),
              "sendByEmployee", Map.of("type", "string", "minLength", 1)))));
  private static final Map<String, Object> WORKFLOW_CONFIG_SCHEMA = Map.of("type", "object", "required", List.of("templateProperty", "workflow"),
      "additionalProperties", false, "properties", Map.of("templateProperty", Map.of("type", "string", "enum",
          java.util.Arrays.stream(BusinessWorkflowNotificationSource.Rule.values()).map(rule -> rule.templateProperty()).toList()),
          "workflow", Map.of("const", true)));
  private static final Map<String, Object> CONFIG_SCHEMA = Map.of("type", "object", "oneOf", List.of(GENERIC_CONFIG_SCHEMA, WORKFLOW_CONFIG_SCHEMA));
  private static final Map<String, Object> GENERIC_INPUT_SCHEMA = Map.of("type", "object", "additionalProperties", false,
      "required", List.of("employeeIds", "payload"), "properties", Map.of(
          "employeeIds", Map.of("type", "array", "items", Map.of("type", "string", "minLength", 1), "minItems", 1, "uniqueItems", true),
          "payload", Map.of("type", "object")));
  private static final Map<String, Object> EMPTY_INPUT_SCHEMA = Map.of("type", "object", "maxProperties", 0);

  private final Supplier<BusinessModelBehaviorProvider> behaviors;
  private final EmployeeNotificationDispatcher dispatcher;
  private final BusinessEventDisclosure disclosure;
  private final EmployeeAggregator employees;
  private final UserCenter users;
  private final NotificationEmployeeAddressParser addresses;
  private final Supplier<BusinessWorkflowNotificationSource> workflowSource;

  public BusinessEventNotificationListener(Supplier<BusinessModelBehaviorProvider> behaviors, EmployeeNotificationDispatcher dispatcher,
      BusinessEventDisclosure disclosure, EmployeeAggregator employees, UserCenter users, NotificationEmployeeAddressParser addresses) {
    this(behaviors, dispatcher, disclosure, employees, users, addresses, () -> null);
  }

  public BusinessEventNotificationListener(Supplier<BusinessModelBehaviorProvider> behaviors, EmployeeNotificationDispatcher dispatcher,
      BusinessEventDisclosure disclosure, EmployeeAggregator employees, UserCenter users, NotificationEmployeeAddressParser addresses,
      Supplier<BusinessWorkflowNotificationSource> workflowSource) {
    this.behaviors = Objects.requireNonNull(behaviors); this.dispatcher = Objects.requireNonNull(dispatcher);
    this.disclosure = Objects.requireNonNull(disclosure); this.employees = Objects.requireNonNull(employees);
    this.users = Objects.requireNonNull(users); this.addresses = Objects.requireNonNull(addresses);
    this.workflowSource = Objects.requireNonNull(workflowSource);
  }

  @Override public String getName() { return NAME; }
  @Override public BusinessEventDeliveryMode getDeliveryMode() { return BusinessEventDeliveryMode.INDEPENDENT; }
  @Override public Map<String, Object> getListenerConfigSchema() { return CONFIG_SCHEMA; }
  @Override public Map<String, Object> getListenerInputSchema() { return GENERIC_INPUT_SCHEMA; }
  @Override public Map<String, Object> getListenerInputSchema(Map<String, Object> config) {
    return config != null && Boolean.TRUE.equals(config.get("workflow")) ? EMPTY_INPUT_SCHEMA : GENERIC_INPUT_SCHEMA;
  }

  @Override
  public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
    if (!JsonSchemas.validate(CONFIG_SCHEMA, delivery.listenerConfig())
        || !JsonSchemas.validate(getListenerInputSchema(delivery.listenerConfig()), delivery.listenerInput())) {
      throw new BusinessException("业务 Event 通知配置或输入无效");
    }
    var behavior = behaviors.get().getBusinessModelBehavior(delivery.consumerModelFullName()).orElseThrow(
        () -> new BusinessException("通知所属业务行为不可用"));
    var subscription = behavior.getEventSubscriptions().get(delivery.subscriptionName());
    if (subscription == null || !NAME.equalsIgnoreCase(subscription.getTarget().getListenerName())
        || !Objects.equals(subscription.getTarget().getConfig(), delivery.listenerConfig())) {
      throw new BusinessException("通知所属订阅与交付配置不一致");
    }
    if (Boolean.TRUE.equals(delivery.listenerConfig().get("workflow"))) {
      sendWorkflow(occurrence, delivery, subscription);
      return;
    }
    var input = delivery.listenerInput();
    if (!JsonSchemas.validate(GENERIC_INPUT_SCHEMA, input)) { throw new BusinessException("业务 Event 通知输入无效"); }
    var recipients = ((List<?>) input.get("employeeIds")).stream().map(String.class::cast).sorted().toList();
    var payload = Json.tryToMap(Json.toJsonString(input.get("payload")))
        .getOrElseThrow(error -> new BusinessException("通知载荷无法读取", error));
    var options = delivery.listenerConfig().containsKey("options") ? Json.tryToBean(DispatchOptions.class,
        Json.toJsonString(delivery.listenerConfig().get("options"))).getOrElseThrow(error -> new BusinessException("通知选项无效", error)) : null;
    String revision = BusinessBehaviorContracts.subscriptionFingerprint(subscription);
    var request = new NotificationDispatchRequest(occurrence.eventId(),
        "business-event:" + delivery.consumerModelFullName() + "#" + delivery.subscriptionName(), revision, recipients,
        (String) delivery.listenerConfig().get("template"), payload, options);
    var result = dispatcher.sendWithResult(request, (employee, channel, frozenAddresses) ->
        recipientDecision(occurrence, employee, channel, frozenAddresses));
    if (!result.isComplete()) { throw new BusinessException("业务 Event 通知尚未完成：" + result.status()); }
  }

  private void sendWorkflow(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery,
      com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition subscription) {
    if (!delivery.listenerInput().isEmpty() || subscription.getTarget().getInputMapping() != null) {
      throw new BusinessException("工作流通知不接受自定义输入映射");
    }
    var source = workflowSource.get();
    if (source == null) { throw new BusinessException("工作流通知受控源事实服务不可用"); }
    var notice = source.read(occurrence, delivery);
    // Unmarked facts retain the original approval notification owner, including in-flight projectionVersion=1 facts.
    if (notice.isEmpty()) { return; }
    var request = new NotificationDispatchRequest(occurrence.eventId(),
        "business-event:" + delivery.consumerModelFullName() + "#" + delivery.subscriptionName(),
        BusinessBehaviorContracts.subscriptionFingerprint(subscription), notice.get().employeeIds(), notice.get().template(), notice.get().payload(), null);
    var result = dispatcher.sendWithResult(request, (employee, channel, frozenAddresses) -> {
      var eligibility = source.eligibility(occurrence, delivery, employee);
      return eligibility.applicable() ? recipientDecision(occurrence, employee, channel, frozenAddresses)
          : new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.SKIP, eligibility.diagnostic());
    });
    if (!result.isComplete()) { throw new BusinessException("工作流业务 Event 通知尚未完成：" + result.status()); }
  }

  private NotificationDeliveryPolicy.Decision recipientDecision(BusinessEventOccurrence<?> occurrence, String employeeId,
      String channel, List<String> frozenAddresses) {
    var employee = employees.getById(employeeId).orElse(null);
    var user = employee == null || employee.getUser() == null ? java.util.Optional.<com.ouroboros.security.rbac.User>empty()
        : users.getUser(employee.getUser()).filter(current -> current.isEnabled());
    if (user.isEmpty()) { return block("notification-recipient-identity-unavailable"); }
    boolean allowed = CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user.get()), () -> disclosure.canRead(occurrence));
    if (!allowed) { return block("notification-recipient-disclosure-denied"); }
    var parsed = addresses.parse(List.of(employeeId), List.of(channel));
    var current = parsed == null ? null : parsed.get(channel);
    if (current == null || current.isEmpty() || !new HashSet<>(current).equals(new HashSet<>(frozenAddresses))) {
      return block("notification-recipient-address-changed");
    }
    return new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.ALLOW, "notification-recipient-authorized");
  }

  private NotificationDeliveryPolicy.Decision block(String diagnostic) {
    return new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.BLOCK, diagnostic);
  }
}
