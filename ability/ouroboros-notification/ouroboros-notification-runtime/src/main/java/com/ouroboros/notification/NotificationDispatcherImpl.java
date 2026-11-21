package com.ouroboros.notification;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.service.NotificationDeliveryPlan;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.constant.NotificationStateConstant;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.notification.service.NotificationService;
import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateCenter;
import com.ouroboros.notification.template.NotificationConfigurationReferences;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonBag;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
@Component
public class NotificationDispatcherImpl implements EmployeeNotificationDispatcher, AddressNotificationDispatcher {

  @Autowired(required = false)
  private List<com.ouroboros.notification.adapter.NotificationAdapterSupplier> managedSuppliers = List.of();

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private NotificationEmployeeAddressParser employeeAddressParser;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private NotificationPayloadSchemaValidator notificationPayloadSchemaValidator;

  @Override
  public void send(Collection<String> employeeIds, String template, Map<String, Object> payload, DispatchOptions options) {
    sendWithResult(employeeIds, template, payload, options);
  }

  @Override
  public DispatchResult sendWithResult(Collection<String> employeeIds, String template,
      Map<String, Object> payload, DispatchOptions options) {
    if (ObjectUtils.isEmpty(employeeIds)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.EMPLOYEE_ID_EMPTY);
    }

    NotificationTemplate notificationTemplate = requireTemplate(template);
    List<String> channels = new ArrayList<>();
    if (options != null && ObjectUtils.isNotEmpty(options.getChannels())) {
      channels.addAll(options.getChannels());
    } else {
      channels.addAll(notificationTemplate.getChannel().keySet());
    }

    Map<String, Collection<String>> addressMap = employeeAddressParser.parse(employeeIds, channels);
    if (ObjectUtils.isEmpty(addressMap) && ObjectUtils.isNotEmpty(channels)) {
      addressMap = new LinkedHashMap<>();
      for (String channel : channels) {
        addressMap.put(channel, Collections.emptyList());
      }
    }

    return dispatch(addressMap, template, payload, options, notificationTemplate);
  }

  @Override
  public void send(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload, DispatchOptions options) {
    dispatch(addressMap, template, payload, options);
  }

  @Override
  public DispatchResult sendWithResult(Map<String, Collection<String>> addresses, String template,
      Map<String, Object> payload, DispatchOptions options) {
    return dispatch(addresses, template, payload, options);
  }

  private DispatchResult dispatch(Map<String, Collection<String>> addressMap, String template,
      Map<String, Object> payload, DispatchOptions options) {
    if (ObjectUtils.isEmpty(addressMap)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY);
    }

    return dispatch(addressMap, template, payload, options, requireTemplate(template));
  }

  private NotificationTemplate requireTemplate(String name) {
    if (ObjectUtils.isEmpty(name)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.TEMPLATE_NAME_EMPTY);
    }
    return NotificationTemplateCenter.getTemplate(name).orElseThrow(() ->
        new NotificationSendException(NotificationSendException.ErrorDefine.TEMPLATE_NOT_EXIST, name));
  }

  private DispatchResult dispatch(Map<String, Collection<String>> addressMap, String template,
      Map<String, Object> payload, DispatchOptions options, NotificationTemplate notificationTemplate) {
    if (ObjectUtils.isEmpty(addressMap)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY);
    }

    Map<String, Object> dispatchPayload = payload instanceof SensitiveNotificationPayload
        ? new SensitiveNotificationPayload(payload) : payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    if (!notificationTemplate.isEnabled()) {
      logger.info("template {} is disabled", template);
      persistFailureNotification(
          addressMap,
          template,
          payload,
          options,
          Collections.emptyMap(),
          resolveFailureChannels(addressMap, notificationTemplate, options),
          String.format("template %s is disabled", template));
      return new DispatchResult(false, List.of(new DispatchResult.ChannelResult("template", false,
          String.format("template %s is disabled", template))));
    }

    // 检查模版渠道是否为空
    Map<String, Map<String, Object>> channelMap = notificationTemplate.getChannel();
    if (ObjectUtils.isEmpty(channelMap)) {
      persistFailureNotification(
          addressMap,
          template,
          payload,
          options,
          Collections.emptyMap(),
          resolveFailureChannels(addressMap, notificationTemplate, options),
          String.format("template %s channel map is empty", template));
      return new DispatchResult(false, List.of(new DispatchResult.ChannelResult("template", false,
          String.format("template %s channel map is empty", template))));
    }

    var resolved = resolveTemplate(notificationTemplate, payload, options);
    var channels = resolved.channels();
    var dispatchOptions = resolved.dispatchOptions();
    var channelSchemaMap = resolved.schemas();
    var channelOptionsMap = resolved.options();
    if (dispatchOptions.getIdempotencyKey() != null && !dispatchOptions.getIdempotencyKey().isBlank()) {
      dispatchPayload.put("__ouroboros_idempotency_key", dispatchOptions.getIdempotencyKey());
    }

    if (!(payload instanceof SensitiveNotificationPayload)) { logger.debug("send address {}, template {}, payload {}, options {}, channels {}, channelSchema {}, channelOptions {}",
        Json.toJsonString(addressMap),
        Json.toJsonString(notificationTemplate),
        Json.toJsonString(payload),
        Json.toJsonString(dispatchOptions),
        Json.toJsonString(channels),
        Json.toJsonString(channelSchemaMap),
        Json.toJsonString(channelOptionsMap)
    );

    }

    // 通知结果集
    List<NotificationStateModel> notificationStateModels = new ArrayList<>();
    List<DispatchResult.ChannelResult> channelResults = new ArrayList<>();
    List<String> attemptedChannels = channels.stream()
        .filter(channel -> ObjectUtils.isNotEmpty(addressMap.get(channel)))
        .collect(Collectors.toList());
    NotificationModel notificationModel = buildNotificationModel(template, dispatchPayload, dispatchOptions, channelOptionsMap, attemptedChannels);
    if (ObjectUtils.isEmpty(attemptedChannels)) {
      notificationService.add(
          notificationModel,
          buildFailureStateModels(notificationModel, addressMap, channels, "selected channels filter out all addresses"));
      return new DispatchResult(false, channels.stream()
          .map(channel -> new DispatchResult.ChannelResult(channel, false, "selected channels filter out all addresses"))
          .toList());
    }

    for (String channel : channels) {
      Collection<String> addresses = addressMap.get(channel);
      if (ObjectUtils.isEmpty(addresses)) {
        continue;
      }

      String sendState = NotificationStateConstant.SUCCESS.getValue();
      String sendFailCause = null;
      try {
        var adapter = NotificationAdapterCenter.getAllAdapter(managedSuppliers).get(channel);
        if (adapter == null) {
          throw new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_ADAPTER_NOT_LOADED, channel);
        }

        NotificationChannelSchema channelSchema = Maps.tryToBean(adapter.getChannelSchemaClass(), channelSchemaMap.get(channel))
            .getOrElse(() -> null);
        NotificationChannelOptions channelOptions = Maps.tryToBean(adapter.getChannelOptionsClass(), channelOptionsMap.get(channel))
            .getOrElse(() -> null);
        // TODO: null检查在这里做还是推到适配器中?
        adapter.send(addresses, channelSchema, dispatchPayload, channelOptions);
      } catch (Exception e) {
        sendFailCause = payload instanceof SensitiveNotificationPayload ? "sensitive notification delivery failed" : e.getMessage();
        sendState = NotificationStateConstant.FAIL.getValue();
      } finally {
        channelResults.add(new DispatchResult.ChannelResult(channel,
            NotificationStateConstant.SUCCESS.getValue().equals(sendState), sendFailCause));
        for (String address : addresses) {
          NotificationStateModel stateModel = new NotificationStateModel();
          stateModel.setNotificationId(notificationModel.getId());
          stateModel.setChannel(channel);
          stateModel.setAddress(address);
          stateModel.setSendState(sendState);
          stateModel.setSendFailCause(sendFailCause);

          notificationStateModels.add(stateModel);
        }
      }
    }

    // 保存通知结果
    notificationService.add(notificationModel, notificationStateModels);
    return new DispatchResult(channelResults.stream().allMatch(DispatchResult.ChannelResult::success), channelResults);
  }

  @Override
  public NotificationDispatchResult sendWithResult(NotificationDispatchRequest request, NotificationDeliveryPolicy policy) {
    Objects.requireNonNull(request, "request"); Objects.requireNonNull(policy, "policy");
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new BusinessException("可靠通知发送需要无外层事务入口，不能持有业务锁等待渠道调用");
    }
    var saved = notificationService.findDelivery(request.sourceFactId(), request.ruleIdentity());
    NotificationDeliveryPlan.Snapshot plan;
    if (saved.isPresent()) {
      plan = saved.get();
      var recipients = plan.units().stream().map(NotificationDeliveryPlan.Unit::recipientId).collect(Collectors.toSet());
      if (!plan.ruleRevision().equals(request.ruleRevision()) || !plan.template().equals(request.template())
          || !recipients.equals(new HashSet<>(request.employeeIds()))) {
        return blocked(plan.notificationId(), "persisted-notification-plan-changed");
      }
      if (plan.isComplete()) { return result(plan); }
    } else {
      NotificationDeliveryPlan intention;
      try { intention = prepareReliablePlan(request); }
      catch (Exception invalid) {
        String detail = invalid instanceof com.ouroboros.exception.OuroborosException classified
            ? String.valueOf(classified.getErrorCode()) : invalid.getClass().getSimpleName();
        return blocked(null, "notification-configuration-blocked:" + detail);
      }
      // Storage failures must propagate to the owning Outbox; they are not configuration outcomes.
      plan = notificationService.prepareDelivery(intention);
    }
    for (var unit : plan.units()) {
      var claim = notificationService.beginAttempt(unit.id());
      if (claim.isEmpty()) { continue; }
      var receipt = dispatchUnit(plan, unit, policy);
      if (!notificationService.completeAttempt(claim.get(), receipt.state(), receipt.diagnostic())) {
        throw new BusinessException("通知发送结果未确认当前尝试；保留持久发送记录等待恢复");
      }
    }
    return result(notificationService.findDelivery(request.sourceFactId(), request.ruleIdentity()).orElseThrow(
        () -> new BusinessException("通知发送计划丢失")));
  }

  private NotificationDeliveryPlan prepareReliablePlan(NotificationDispatchRequest request) {
    if (request.employeeIds().isEmpty() || request.employeeIds().stream().anyMatch(id -> id == null || id.isBlank())) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.EMPLOYEE_ID_EMPTY);
    }
    var template = NotificationTemplateCenter.getTemplate(request.template()).orElseThrow(
        () -> new NotificationSendException(NotificationSendException.ErrorDefine.TEMPLATE_NOT_EXIST, request.template()));
    if (!template.isEnabled() || ObjectUtils.isEmpty(template.getChannel())) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_SCHEMA_ERROR, "template unavailable");
    }
    var resolved = resolveTemplate(template, request.payload(), request.options());
    var recipients = employeeAddressParser.resolve(request.employeeIds(), resolved.channels(), resolved.options());
    var destinations = new ArrayList<NotificationDeliveryPlan.Destination>();
    for (String employee : new LinkedHashSet<>(request.employeeIds())) {
      for (String channel : resolved.channels()) {
        var channelRecipients = recipients.get(channel);
        var values = channelRecipients == null ? null : channelRecipients.addresses().get(employee);
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
          throw new NotificationSendException(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY);
        }
        destinations.add(new NotificationDeliveryPlan.Destination(employee, channel, new ArrayList<>(values)));
      }
    }
    var schemas = new LinkedHashMap<String, Object>();
    var options = new LinkedHashMap<String, Object>();
    resolved.channels().forEach(channel -> { schemas.put(channel, resolved.schemas().get(channel)); options.put(channel, recipients.get(channel).channelOptions()); });
    return new NotificationDeliveryPlan(request.sourceFactId(), request.ruleIdentity(), request.ruleRevision(),
        request.template(), request.payload(), options, resolved.dispatchOptions().getSendByEmployee(), destinations, schemas);
  }

  private UnitResult dispatchUnit(NotificationDeliveryPlan.Snapshot plan, NotificationDeliveryPlan.Unit unit,
      NotificationDeliveryPolicy policy) {
    NotificationDeliveryPolicy.Decision decision;
    try { decision = Objects.requireNonNull(policy.evaluate(unit.recipientId(), unit.channel(), unit.addresses()), "recipient decision"); }
    catch (Exception failure) { return channelResult(NotificationChannelResult.Outcome.BLOCKED, "recipient-check-failed:" + failure.getClass().getSimpleName()); }
    if (decision.disposition() == NotificationDeliveryPolicy.Disposition.SKIP) {
      return new UnitResult(NotificationStateConstant.SKIPPED, decision.diagnostic());
    }
    if (decision.disposition() == NotificationDeliveryPolicy.Disposition.BLOCK) {
      return channelResult(NotificationChannelResult.Outcome.BLOCKED, decision.diagnostic());
    }
    com.ouroboros.notification.adapter.NotificationAdapter<NotificationChannelSchema, NotificationChannelOptions> adapter;
    NotificationChannelSchema schema;
    NotificationChannelOptions options;
    Map<String, Object> routingContext;
    try {
      var currentTemplate = NotificationTemplateCenter.getTemplate(plan.template());
      if (currentTemplate.isEmpty() || !currentTemplate.get().isEnabled() || currentTemplate.get().getChannel() == null
          || !currentTemplate.get().getChannel().containsKey(unit.channel())) {
        return channelResult(NotificationChannelResult.Outcome.BLOCKED, "notification-template-unavailable");
      }
      adapter = NotificationAdapterCenter.getAllAdapter(managedSuppliers).get(unit.channel());
      if (adapter == null) { return channelResult(NotificationChannelResult.Outcome.BLOCKED, "notification-channel-adapter-unavailable"); }
      NotificationAdapterCenter.findSupplier(unit.channel(), managedSuppliers).flatMap(supplier -> supplier.getEmployeeRecipients())
          .ifPresent(recipients -> recipients.verify(unit.recipientId(), unit.addresses(), jsonMap(plan.channelOptions().get(unit.channel()))));
      if (!plan.channelSchemas().containsKey(unit.channel())) {
        return channelResult(NotificationChannelResult.Outcome.BLOCKED, "notification-channel-schema-not-frozen");
      }
      schema = Maps.tryToBean(adapter.getChannelSchemaClass(), jsonMap(plan.channelSchemas().get(unit.channel())))
          .getOrElseThrow(error -> new BusinessException("通知渠道架构转换失败", error));
      var frozenOptions = jsonMap(plan.channelOptions().get(unit.channel()));
      routingContext = jsonMap(frozenOptions.remove(com.ouroboros.notification.employee.NotificationChannelRecipients.ROUTING_CONTEXT));
      options = Maps.tryToBean(adapter.getChannelOptionsClass(), frozenOptions)
          .getOrElseThrow(error -> new BusinessException("通知渠道选项转换失败", error));
      if (schema == null || options == null) { return channelResult(NotificationChannelResult.Outcome.BLOCKED, "notification-channel-configuration-invalid"); }
    } catch (Exception failure) {
      return channelResult(NotificationChannelResult.Outcome.BLOCKED, "notification-channel-configuration-invalid:" + failure.getClass().getSimpleName());
    }
    try {
      var receipt = routingContext.isEmpty() ? adapter.sendWithResult(unit.id(), unit.addresses(), schema, plan.payload(), options)
          : adapter.sendWithResult(unit.id(), unit.addresses(), schema, plan.payload(), options, routingContext);
      if (receipt == null) { return channelResult(NotificationChannelResult.Outcome.UNKNOWN, "notification-channel-receipt-missing"); }
      if (receipt.isPresent()) { return channelResult(receipt.get().outcome(), receipt.get().diagnostic()); }
      // Call the original SPI proxy so existing transaction/interceptor semantics are preserved.
      adapter.send(unit.addresses(), schema, plan.payload(), options);
      return channelResult(NotificationChannelResult.Outcome.ACCEPTED, "legacy-channel-accepted-collection");
    } catch (Exception failure) {
      return channelResult(NotificationChannelResult.Outcome.UNKNOWN, "notification-channel-outcome-unknown:" + failure.getClass().getSimpleName());
    }
  }

  private UnitResult channelResult(NotificationChannelResult.Outcome outcome, String diagnostic) {
    return new UnitResult(switch (outcome) {
      case ACCEPTED -> NotificationStateConstant.SUCCESS;
      case RETRYABLE -> NotificationStateConstant.RETRYABLE;
      case BLOCKED -> NotificationStateConstant.BLOCKED;
      case UNKNOWN -> NotificationStateConstant.UNKNOWN;
    }, diagnostic);
  }

  private record UnitResult(NotificationStateConstant state, String diagnostic) { }

  private NotificationDispatchResult blocked(String notificationId, String diagnostic) {
    return new NotificationDispatchResult(notificationId, NotificationDispatchResult.Status.BLOCKED, diagnostic, List.of());
  }

  private NotificationDispatchResult result(NotificationDeliveryPlan.Snapshot plan) {
    var status = plan.isComplete() ? NotificationDispatchResult.Status.COMPLETE
        : plan.units().stream().anyMatch(unit -> unit.state() == NotificationStateConstant.UNKNOWN) ? NotificationDispatchResult.Status.UNKNOWN
        : plan.units().stream().anyMatch(unit -> unit.state() == NotificationStateConstant.BLOCKED) ? NotificationDispatchResult.Status.BLOCKED
        : NotificationDispatchResult.Status.PENDING;
    return new NotificationDispatchResult(plan.notificationId(), status, "notification-" + status.name().toLowerCase(Locale.ROOT),
        plan.units().stream().map(unit -> new NotificationDispatchResult.Unit(unit.id(), unit.recipientId(), unit.channel(), unit.state(), unit.diagnostic())).toList());
  }

  private ResolvedTemplate resolveTemplate(NotificationTemplate notificationTemplate, Map<String, Object> payload, DispatchOptions options) {
    Map<String, Map<String, Object>> channelMap = notificationTemplate.getChannel();
    // 提取模版中的渠道schema
    Map<String, Map<String, Object>> channelSchemaMap = channelMap.keySet().stream()
        .collect(
            Collectors.toMap(
                channel -> channel,
                channel -> {
                  Object templateSchema = channelMap.get(channel).get("schema");
                  return jsonMap(templateSchema);
                },
                (v1, v2) -> v2
            )
        );

    // 提取模版中的渠道options
    Map<String, Map<String, Object>> channelOptionsMap = channelMap.keySet().stream()
        .collect(
            Collectors.toMap(
                channel -> channel,
                channel -> {
                  Object templateOptions = channelMap.get(channel).get("options");
                  return jsonMap(templateOptions);
                },
                (v1, v2) -> v2
            )
        );

    // 检查payload参数是否符合规范
    if (ObjectUtils.isNotEmpty(notificationTemplate.getPayloadSchema())) {
      if (ObjectUtils.isEmpty(payload)) {
        throw new NotificationSendException(NotificationSendException.ErrorDefine.PAYLOAD_PARAM_EMPTY);
      }

      var validationResult = notificationPayloadSchemaValidator.validate(notificationTemplate.getPayloadSchema(), payload);
      if (!validationResult.isValid()) {
        throw new NotificationSendException(
            NotificationSendException.ErrorDefine.PAYLOAD_PARAM_NON_CONFORMITY,
            validationResult.toMessageString());
      }
    }

    // 通知渠道
    List<String> channels = new ArrayList<>();

    // 如果dispatchOptions中的channel为空，则默认使用模版中的全部channel
    DispatchOptions dispatchOptions = options != null ? options : new DispatchOptions();
    if (ObjectUtils.isNotEmpty(dispatchOptions.getChannels())) {
      channels.addAll(dispatchOptions.getChannels());
    } else {
      channels.addAll(notificationTemplate.getChannel().keySet());
    }

    // 检查发送渠道是否正确
    for (String channel : channels) {
      // 检查需要推送通知的渠道是否在模版定义中
      if (!channelSchemaMap.containsKey(channel)) {
        throw new NotificationSendException(NotificationSendException.ErrorDefine.OPTIONS_CHANNEL_NOT_DEFINE);
      }
    }

    // 如果模版渠道选项不为空，则将模版渠道选项合并到dispatchOptions中
    channelOptionsMap.forEach((channel, templateChannelOptionsMap) -> {
      Map<String, NotificationChannelOptions> dispatchAllChannelOptionsMap = dispatchOptions.getChannelOptions() == null
          ? new HashMap<>()
          : dispatchOptions.getChannelOptions();

      NotificationChannelOptions dispatchChannelOptions = dispatchAllChannelOptionsMap.get(channel);
      Map<String, Object> dispatchChannelOptionsMap = dispatchChannelOptions != null
          ? Maps.fromBean(dispatchChannelOptions)
          : new HashMap<>();

      // 移除值为空的key
      dispatchChannelOptionsMap.keySet().removeIf(next -> dispatchChannelOptionsMap.get(next) == null);

      channelOptionsMap.put(channel, Maps.merge(templateChannelOptionsMap, dispatchChannelOptionsMap));
    });

    // 渠道schema\options替换配置值
    channelSchemaMap.forEach((channel, schemaMap) -> recursionReplaceConfiguration(schemaMap));
    channelOptionsMap.forEach((channel, optionsMap) -> recursionReplaceConfiguration(optionsMap));

    return new ResolvedTemplate(List.copyOf(new LinkedHashSet<>(channels)), channelSchemaMap, channelOptionsMap, dispatchOptions);
  }

  private Map<String, Object> jsonMap(Object value) {
    return Json.tryToMap(Json.toJsonString(value == null ? Map.of() : value))
        .getOrElseThrow(error -> new BusinessException("通知渠道配置必须为 JSON 对象", error));
  }

  private record ResolvedTemplate(List<String> channels, Map<String, Map<String, Object>> schemas,
                                  Map<String, Map<String, Object>> options, DispatchOptions dispatchOptions) { }

  private void recursionReplaceConfiguration(Map<String, Object> map) {
    Object resolved = NotificationConfigurationReferences.resolve(map, "", (path, key) ->
        configurationService.get(String.class, key).orElseThrow(() ->
            new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_SCHEMA_ERROR, "配置不存在: " + key)));
    if (resolved instanceof Map<?, ?> values) {
      values.forEach((key, value) -> map.put(String.valueOf(key), value));
    }
  }

  private void persistFailureNotification(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload,
      DispatchOptions options, Map<String, Map<String, Object>> channelOptionsMap, Collection<String> channels, String sendFailCause) {
    NotificationModel notificationModel = buildNotificationModel(template, payload, options, channelOptionsMap, channels);
    List<NotificationStateModel> notificationStateModels = buildFailureStateModels(notificationModel, addressMap, channels, sendFailCause);
    notificationService.add(notificationModel, notificationStateModels);
  }

  private Collection<String> resolveFailureChannels(Map<String, Collection<String>> addressMap, NotificationTemplate notificationTemplate, DispatchOptions options) {
    Set<String> channels = new LinkedHashSet<>();

    if (options != null && ObjectUtils.isNotEmpty(options.getChannels())) {
      channels.addAll(options.getChannels());
    }

    if (ObjectUtils.isEmpty(channels) && notificationTemplate != null && ObjectUtils.isNotEmpty(notificationTemplate.getChannel())) {
      channels.addAll(notificationTemplate.getChannel().keySet());
    }

    if (ObjectUtils.isEmpty(channels) && ObjectUtils.isNotEmpty(addressMap)) {
      channels.addAll(addressMap.keySet());
    }

    return channels;
  }

  private List<NotificationStateModel> buildFailureStateModels(NotificationModel notificationModel, Map<String, Collection<String>> addressMap,
      Collection<String> channels, String sendFailCause) {
    List<NotificationStateModel> notificationStateModels = new ArrayList<>();

    for (String channel : channels) {
      Collection<String> addresses = addressMap.get(channel);
      if (ObjectUtils.isEmpty(addresses)) {
        NotificationStateModel stateModel = new NotificationStateModel();
        stateModel.setNotificationId(notificationModel.getId());
        stateModel.setChannel(channel);
        stateModel.setSendState(NotificationStateConstant.FAIL.getValue());
        stateModel.setSendFailCause(sendFailCause);
        notificationStateModels.add(stateModel);
        continue;
      }

      for (String address : addresses) {
        NotificationStateModel stateModel = new NotificationStateModel();
        stateModel.setNotificationId(notificationModel.getId());
        stateModel.setChannel(channel);
        stateModel.setAddress(address);
        stateModel.setSendState(NotificationStateConstant.FAIL.getValue());
        stateModel.setSendFailCause(sendFailCause);
        notificationStateModels.add(stateModel);
      }
    }

    return notificationStateModels;
  }

  /**
   * 构建NotificationModel
   *
   * @param template
   * @param payload
   * @param options
   * @return
   */
  private NotificationModel buildNotificationModel(String template, Map<String, Object> payload, DispatchOptions options,
      Map<String, Map<String, Object>> channelOptionsMap, Collection<String> attemptedChannels) {
    NotificationModel model = new NotificationModel();
    model.setTemplate(template);

    if (ObjectUtils.isNotEmpty(payload) && !(payload instanceof SensitiveNotificationPayload)) {
      model.setPayload(new JsonBag(payload));
    }

    Map<String, Object> channelOptions = channelOptionsMap.entrySet().stream()
        .filter(entry -> attemptedChannels.contains(entry.getKey()))
        .filter(entry -> ObjectUtils.isNotEmpty(entry.getValue()))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue(),
            (v1, v2) -> v2,
            LinkedHashMap::new));
    if (ObjectUtils.isNotEmpty(channelOptions) && !(payload instanceof SensitiveNotificationPayload)) {
      model.setChannelOptions(new JsonBag(channelOptions));
    }

    if (options != null) {
      model.setSendByEmployee(options.getSendByEmployee());
    }

    if (options != null) {
      model.setIdempotencyKey(options.getIdempotencyKey());
    }
    model.setSendTime(LocalDateTime.now());
    return model;
  }
}
