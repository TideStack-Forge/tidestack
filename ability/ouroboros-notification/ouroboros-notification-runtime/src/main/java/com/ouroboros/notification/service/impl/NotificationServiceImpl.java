package com.ouroboros.notification.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.constant.NotificationStateConstant;
import com.ouroboros.notification.service.NotificationDeliveryPlan;
import com.ouroboros.notification.service.NotificationCoreService;
import com.ouroboros.notification.service.model.NotificationAttemptModel;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonBag;
import com.ouroboros.notification.service.NotificationService;
import com.ouroboros.notification.service.NotificationStateService;
import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;

/**
 * @author liansz
 */
@Service
public class NotificationServiceImpl implements NotificationService {

  @Autowired
  NotificationCoreService coreService;

  @Autowired
  NotificationStateService stateService;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Value("${ouroboros.notification.delivery.attempt-timeout-seconds:120}")
  private long attemptTimeoutSeconds = 120;

  private Clock clock = Clock.systemUTC();
  private volatile String retentionCursor;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void add(NotificationModel model, Collection<NotificationStateModel> stateModel) {
    String notificationId = coreService.add(model).getId();
    stateModel.forEach(s -> s.setNotificationId(notificationId));
    stateService.add(stateModel);
  }

  @Override
  public NotificationDeliveryPlan.Snapshot prepareDelivery(NotificationDeliveryPlan plan) {
    requireText(plan.sourceFactId(), "sourceFactId");
    requireText(plan.ruleIdentity(), "ruleIdentity");
    requireText(plan.ruleRevision(), "ruleRevision");
    requireText(plan.template(), "template");
    for (var destination : plan.destinations()) {
      requireText(destination.recipientId(), "recipientId"); requireText(destination.channel(), "channel");
      if (destination.addresses().isEmpty()) { throw new BusinessException("通知发送单元缺少地址"); }
      destination.addresses().forEach(address -> requireText(address, "address"));
    }
    if (plan.destinations().isEmpty()) { throw new BusinessException("通知发送计划必须明确发送单元，不能将空结果确认为成功"); }
    String id = digest(List.of(plan.sourceFactId(), plan.ruleIdentity()));
    String fingerprint = fingerprint(plan);
    try {
      return independently(models -> {
        var current = models.notifications().get(id).getOrElseThrow(error -> storage("读取通知意图失败", error));
        if (current == null) {
          var notification = new NotificationModel();
          notification.setId(id); notification.setTemplate(plan.template());
          notification.setSourceFactId(plan.sourceFactId()); notification.setRuleIdentity(plan.ruleIdentity());
          notification.setRuleRevision(plan.ruleRevision()); notification.setPlanFingerprint(fingerprint);
          notification.setPayload(new JsonBag(jsonObject(plan.payload())));
          notification.setChannelOptions(new JsonBag(jsonObject(plan.channelOptions())));
          notification.setChannelSchemas(new JsonBag(jsonObject(plan.channelSchemas())));
          notification.setSendByEmployee(plan.sendByEmployee()); notification.setSendTime(now());
          models.notifications().insert(notification).getOrElseThrow(error -> storage("持久化通知意图失败", error));
          var units = new ArrayList<NotificationStateModel>();
          var keys = new java.util.HashSet<String>();
          for (var destination : plan.destinations()) {
            var unit = new NotificationStateModel();
            unit.setId(digest(List.of(id, destination.recipientId(), destination.channel())));
            if (!keys.add(unit.getId())) { throw new BusinessException("通知发送单元的收件人 / 渠道重复"); }
            unit.setNotificationId(id); unit.setRecipientId(destination.recipientId());
            unit.setChannel(destination.channel()); unit.setAddress(destination.address());
            unit.setDeliveryAddresses(new JsonBag(Map.of("values", destination.addresses())));
            unit.setSendState(NotificationStateConstant.PENDING.getValue()); unit.setAttempt(0);
            units.add(unit);
          }
          models.units().batchInsert(units).getOrElseThrow(error -> storage("持久化通知发送单元失败", error));
        }
        return prepared(models, id, fingerprint);
      });
    } catch (RuntimeException failure) {
      // A competing complete plan may win the primary key. Roll back first, then read its committed plan.
      if (!isUniqueConflict(failure)) { throw failure; }
      return independently(models -> prepared(models, id, fingerprint));
    }
  }

  @Override
  public Optional<NotificationDeliveryPlan.Snapshot> findDelivery(String sourceFactId, String ruleIdentity) {
    requireText(sourceFactId, "sourceFactId"); requireText(ruleIdentity, "ruleIdentity");
    return independently(models -> Optional.ofNullable(models.notifications().get(digest(List.of(sourceFactId, ruleIdentity)))
        .getOrElseThrow(error -> storage("读取通知意图失败", error))).map(model -> snapshot(models, model)));
  }

  @Override
  public Optional<NotificationDeliveryPlan.Claim> beginAttempt(String unitId) {
    return independently(models -> {
      var unit = lock(models.units(), NotificationStateModel.class, unitId).orElseThrow(() -> new BusinessException("通知发送单元不存在"));
      var state = NotificationStateConstant.of(unit.getSendState());
      if (state == NotificationStateConstant.SENDING) {
        expire(models, unit);
        return Optional.empty();
      }
      if (!List.of(NotificationStateConstant.PENDING, NotificationStateConstant.RETRYABLE, NotificationStateConstant.BLOCKED).contains(state)) {
        return Optional.empty();
      }
      if (attemptTimeoutSeconds < 1) { throw new BusinessException("通知发送尝试超时必须为正数"); }
      String token = UUID.randomUUID().toString();
      int attempt = unit.getAttempt() + 1;
      LocalDateTime started = now();
      var record = new NotificationAttemptModel();
      record.setId(token); record.setUnitId(unitId); record.setAttempt(attempt);
      record.setStartedAt(started); record.setDeadlineAt(started.plusSeconds(attemptTimeoutSeconds));
      record.setOutcome(NotificationStateConstant.SENDING.getValue());
      models.attempts().insert(record).getOrElseThrow(error -> storage("保存通知发送尝试失败", error));
      update(models.units(), unitId, Map.of("sendState", NotificationStateConstant.SENDING.getValue(),
          "attempt", attempt, "attemptToken", token, "attemptDeadline", record.getDeadlineAt()));
      return Optional.of(new NotificationDeliveryPlan.Claim(unitId, token, attempt));
    });
  }

  @Override
  public boolean completeAttempt(NotificationDeliveryPlan.Claim claim, NotificationStateConstant outcome, String diagnostic) {
    if (!List.of(NotificationStateConstant.SUCCESS, NotificationStateConstant.RETRYABLE, NotificationStateConstant.BLOCKED,
        NotificationStateConstant.SKIPPED, NotificationStateConstant.UNKNOWN).contains(outcome)) {
      throw new BusinessException("通知发送结果不是可确认状态");
    }
    if (outcome != NotificationStateConstant.SUCCESS) { requireText(diagnostic, "diagnostic"); }
    return independently(models -> {
      var unit = lock(models.units(), NotificationStateModel.class, claim.unitId()).orElseThrow(() -> new BusinessException("通知发送单元不存在"));
      if (!Objects.equals(unit.getAttemptToken(), claim.token()) || !Objects.equals(unit.getAttempt(), claim.attempt())
          || !List.of(NotificationStateConstant.SENDING.getValue(), NotificationStateConstant.UNKNOWN.getValue()).contains(unit.getSendState())) {
        return false;
      }
      if (outcome == NotificationStateConstant.UNKNOWN && NotificationStateConstant.UNKNOWN.getValue().equals(unit.getSendState())) { return false; }
      finish(models, unit, outcome, diagnostic, null, null);
      return true;
    });
  }

  @Override
  public boolean resolveUnknown(String unitId, String token, NotificationStateConstant outcome, String operatorRef, String evidence) {
    requireText(operatorRef, "operatorRef"); requireText(evidence, "evidence");
    if (!List.of(NotificationStateConstant.SUCCESS, NotificationStateConstant.SKIPPED, NotificationStateConstant.RETRYABLE).contains(outcome)) {
      throw new BusinessException("未知通知结果只能依据证据确认为成功、跳过或允许重试");
    }
    return independently(models -> {
      var unit = lock(models.units(), NotificationStateModel.class, unitId).orElseThrow(() -> new BusinessException("通知发送单元不存在"));
      expire(models, unit);
      if (!NotificationStateConstant.UNKNOWN.getValue().equals(unit.getSendState()) || !Objects.equals(token, unit.getAttemptToken())) {
        return false;
      }
      finish(models, unit, outcome, "explicit-resolution", operatorRef, evidence);
      return true;
    });
  }

  private NotificationDeliveryPlan.Snapshot prepared(DeliveryModels models, String id, String fingerprint) {
    var notification = lock(models.notifications(), NotificationModel.class, id).orElseThrow(() -> new BusinessException("通知意图并发创建后不可见"));
    if (!fingerprint.equals(notification.getPlanFingerprint())) {
      throw new BusinessException("同一源事实 / 规则的发送计划已固定；修改规则或收件人不能隐式重发");
    }
    return snapshot(models, notification);
  }

  @Override
  public int clearExpiredDeliveryContents(int retentionDays, int batchSize) {
    if (retentionDays < 1 || batchSize < 1 || batchSize > 1000) {
      throw new BusinessException("通知内容保留期和清理批量必须为正数，批量不能超过 1000");
    }
    return independently(models -> {
      var cutoff = now().minusDays(retentionDays);
      var query = models.notifications().query().where(
          com.ouroboros.data.dsl.query.Query.field("contentClearedAt").eq(null),
          com.ouroboros.data.dsl.query.Query.field("sendTime").lt(cutoff))
          .andWhere(SExpression.create(Operators.IS_NOT_NULL, SExpression.field("sourceFactId")));
      String after = retentionCursor;
      if (after != null) { query.andWhere(com.ouroboros.data.dsl.query.Query.field("id").gt(after)); }
      var candidates = query.clause("ORDER", Map.of("id", "asc")).clause("LIMIT", batchSize).execute()
          .getOrElseThrow(error -> storage("读取通知清理候选失败", error));
      int cleared = 0;
      for (var candidate : candidates) {
        var notification = lock(models.notifications(), NotificationModel.class, candidate.getId()).orElseThrow();
        if (notification.getContentClearedAt() != null) { continue; }
        var units = models.units().query().where(Map.of("notificationId", candidate.getId())).execute()
            .getOrElseThrow(error -> storage("读取通知清理状态失败", error));
        if (units.isEmpty() || units.stream().anyMatch(unit -> unit.getFinishedAt() == null
            || !unit.getFinishedAt().isBefore(cutoff)
            || !(NotificationStateConstant.SUCCESS.getValue().equals(unit.getSendState())
                || NotificationStateConstant.SKIPPED.getValue().equals(unit.getSendState())))) { continue; }
        update(models.notifications(), candidate.getId(), Map.of("payload", Map.of(), "channelOptions", Map.of(),
            "channelSchemas", Map.of(), "contentClearedAt", now()));
        var changes = new LinkedHashMap<String, Object>();
        changes.put("address", null); changes.put("deliveryAddresses", null); changes.put("sendFailCause", null);
        for (var unit : units) {
          update(models.units(), unit.getId(), changes);
          var attemptChanges = new LinkedHashMap<String, Object>();
          attemptChanges.put("diagnostic", null); attemptChanges.put("resolutionEvidence", null);
          models.attempts().update(Map.of("unitId", unit.getId()), attemptChanges)
              .getOrElseThrow(error -> storage("清理通知尝试内容失败", error));
        }
        cleared++;
      }
      // Move past unfinished records; the next sweep revisits them without starving later terminal plans.
      retentionCursor = candidates.size() < batchSize ? null : candidates.getLast().getId();
      return cleared;
    });
  }

  private NotificationDeliveryPlan.Snapshot snapshot(DeliveryModels models, NotificationModel notification) {
    var units = models.units().query().where(Map.of("notificationId", notification.getId())).execute()
        .getOrElseThrow(error -> storage("读取通知发送单元失败", error)).stream()
        .sorted(java.util.Comparator.comparing(NotificationStateModel::getRecipientId).thenComparing(NotificationStateModel::getChannel))
        .map(unit -> new NotificationDeliveryPlan.Unit(unit.getId(), unit.getRecipientId(), unit.getChannel(), addresses(unit),
            NotificationStateConstant.of(unit.getSendState()), unit.getAttempt(), unit.getAttemptToken(),
            unit.getAttemptDeadline(), unit.getSendFailCause())).toList();
    return new NotificationDeliveryPlan.Snapshot(notification.getId(), notification.getRuleRevision(), notification.getTemplate(),
        notification.getPayload().getMap(), notification.getChannelOptions().getMap(), notification.getSendByEmployee(), units,
        notification.getChannelSchemas() == null ? Map.of() : notification.getChannelSchemas().getMap());
  }

  private List<String> addresses(NotificationStateModel unit) {
    if (unit.getDeliveryAddresses() == null) { return unit.getAddress() == null ? List.of() : List.of(unit.getAddress()); }
    Object values = unit.getDeliveryAddresses().getMap().get("values");
    if (!(values instanceof List<?> list) || list.stream().anyMatch(value -> !(value instanceof String))) {
      throw new BusinessException("通知发送单元地址快照无效");
    }
    return list.stream().map(String.class::cast).toList();
  }

  private void expire(DeliveryModels models, NotificationStateModel unit) {
    if (NotificationStateConstant.SENDING.getValue().equals(unit.getSendState()) && !unit.getAttemptDeadline().isAfter(now())) {
      finish(models, unit, NotificationStateConstant.UNKNOWN, "attempt-expired-outcome-unknown", null, null);
    }
  }

  private void finish(DeliveryModels models, NotificationStateModel unit, NotificationStateConstant outcome,
      String diagnostic, String operatorRef, String evidence) {
    Map<String, Object> attemptChange = new LinkedHashMap<>();
    if (operatorRef == null) {
      attemptChange.put("outcome", outcome.getValue()); attemptChange.put("diagnostic", diagnostic);
      attemptChange.put("completedAt", now());
    } else {
      attemptChange.put("resolvedOutcome", outcome.getValue());
      attemptChange.put("resolvedBy", operatorRef); attemptChange.put("resolvedAt", now());
      attemptChange.put("resolutionEvidence", evidence);
    }
    update(models.attempts(), unit.getAttemptToken(), attemptChange);
    Map<String, Object> unitChange = new LinkedHashMap<>();
    unitChange.put("sendState", outcome.getValue()); unitChange.put("sendFailCause", diagnostic);
    if (outcome == NotificationStateConstant.SUCCESS || outcome == NotificationStateConstant.SKIPPED) {
      unitChange.put("finishedAt", now());
    }
    if (outcome != NotificationStateConstant.UNKNOWN) {
      unitChange.put("attemptToken", null); unitChange.put("attemptDeadline", null);
    }
    update(models.units(), unit.getId(), unitChange);
    unit.setSendState(outcome.getValue());
  }

  private <M> Optional<M> lock(TypedDataModel<String, M> typed, Class<M> type, String id) {
    var model = typed.getDataModel();
    var query = QueryStatement.builder().from(model.getFullName(), model.getRawName())
        .select(model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(SExpression.create(Operators.EQ, SExpression.field("id"), SExpression.constant(id))).build();
    var rows = model.queryForUpdate(query).getOrElseThrow(error -> storage("锁定通知记录失败", error));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().toBean(type));
  }

  private void update(TypedDataModel<String, ?> model, String id, Map<String, Object> change) {
    Long count = model.update(id, change).getOrElseThrow(error -> storage("更新通知记录失败", error));
    if (!Long.valueOf(1).equals(count)) { throw new BusinessException("通知记录未更新唯一目标"); }
  }

  private <T> T independently(Function<DeliveryModels, T> operation) {
    if (!(transactionManager instanceof DataSourceTransactionManager local) || local.getDataSource() == null) {
      throw new BusinessException("通知独立持久化需要已验证的本地事务资源");
    }
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return DataModelExecutionContext.withPlatformWrite(() -> transaction.execute(status -> {
      var models = new DeliveryModels(typed(NotificationModel.class), typed(NotificationStateModel.class), typed(NotificationAttemptModel.class));
      var connection = DataSourceUtils.getConnection(local.getDataSource());
      try {
        for (var typed : List.of(models.notifications(), models.units(), models.attempts())) {
          Object resource = typed.getDataModel().getAdapter().requireTransactionResource()
              .getOrElseThrow(error -> storage("通知模型无法证明实际事务参与", error));
          if (resource != connection) { throw new BusinessException("通知意图 / 单元 / 尝试必须参与同一独立提交资源"); }
        }
        return operation.apply(models);
      } finally {
        DataSourceUtils.releaseConnection(connection, local.getDataSource());
      }
    }));
  }

  private <M> TypedDataModel<String, M> typed(Class<M> type) {
    return TypedDataModelCenter.getDataModel(String.class, type).orElseThrow(() -> new BusinessException("通知持久模型不可用: " + type.getSimpleName()));
  }

  private record DeliveryModels(TypedDataModel<String, NotificationModel> notifications,
                                TypedDataModel<String, NotificationStateModel> units,
                                TypedDataModel<String, NotificationAttemptModel> attempts) { }

  private String fingerprint(NotificationDeliveryPlan plan) {
    Map<String, Object> values = new TreeMap<>();
    values.put("sourceFactId", plan.sourceFactId()); values.put("ruleIdentity", plan.ruleIdentity());
    values.put("ruleRevision", plan.ruleRevision()); values.put("template", plan.template());
    values.put("payload", jsonObject(plan.payload())); values.put("channelOptions", jsonObject(plan.channelOptions()));
    values.put("sendByEmployee", plan.sendByEmployee());
    if (!plan.channelSchemas().isEmpty()) { values.put("channelSchemas", jsonObject(plan.channelSchemas())); }
    values.put("destinations", plan.destinations().stream().map(unit -> List.of(unit.recipientId(), unit.channel(), unit.addresses().size() == 1 ? unit.address() : unit.addresses()))
        .sorted(java.util.Comparator.comparing(Json::toJsonString)).toList());
    return digest(canonical(values));
  }

  private Map<String, Object> jsonObject(Map<String, Object> value) {
    return value == null ? Map.of() : Json.tryToMap(Json.toJsonString(value)).getOrElseThrow(error -> storage("通知发送计划必须能序列化", error));
  }

  private Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      var result = new TreeMap<String, Object>();
      map.forEach((key, nested) -> result.put(String.valueOf(key), canonical(nested)));
      return result;
    }
    if (value instanceof Collection<?> values) { return values.stream().map(this::canonical).toList(); }
    return value;
  }

  private String digest(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.toJsonString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("JVM 缺少 SHA-256", error);
    }
  }

  private boolean isUniqueConflict(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof UniqueConstraintViolationException) { return true; }
    }
    return false;
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) { throw new BusinessException("通知发送计划缺少 " + field); }
  }

  private BusinessException storage(String message, Throwable cause) { return new BusinessException(message, cause); }

  private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), clock.getZone()); }
}
