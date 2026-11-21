package com.ouroboros.notification.internal.message.service.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.util.Json;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.notification.internal.message.adapter.InternalMessageNotificationChannelOptions;
import com.ouroboros.notification.internal.message.constant.InternalMessageStateEnum;
import com.ouroboros.notification.internal.message.service.NotificationInternalMessageService;
import com.ouroboros.notification.internal.message.service.model.NotificationInternalMessageModel;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.websocket.StompMessageSender;

/**
 * @author liansz
 */
@Service
public class NotificationInternalMessageServiceImpl
    extends SimpleModelServiceImpl<String, NotificationInternalMessageModel>
    implements NotificationInternalMessageService {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NotificationInternalMessageServiceImpl.class);

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private StompMessageSender stompMessageSender;

  @Autowired
  private EmployeeAggregator employeeAggregator;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void sendNotification(List<String> employeeIds, String title, String content,
                               InternalMessageNotificationChannelOptions channelOptions) {
    add(messages(employeeIds, title, content, channelOptions));
  }

  private List<NotificationInternalMessageModel> messages(List<String> employeeIds, String title, String content,
      InternalMessageNotificationChannelOptions channelOptions) {
    Map<String, Boolean> platformMap = channelOptions.getPlatform() != null
        ? channelOptions.getPlatform()
        : Collections.emptyMap();

    String platform = platformMap.keySet().stream()
        .filter(key -> Objects.equals(platformMap.get(key), true))
        .collect(Collectors.joining(","));

    List<NotificationInternalMessageModel> models = employeeIds.stream()
        .map(employeeId -> Tuple.of(employeeId, new NotificationInternalMessageModel()))
        .peek(tuple -> tuple._2().setEmployee(tuple._1()))
        .map(Tuple2::_2)
        .peek(model -> model.setPlatform(platform))
        .peek(model -> model.setTitle(title))
        .peek(model -> model.setContent(content))
        .peek(model -> model.setType(channelOptions.getType()))
        .peek(model -> model.setAction(channelOptions.getAction()))
        .peek(model -> model.setReceivingTime(LocalDateTime.now()))
        .peek(model -> model.setState(InternalMessageStateEnum.UNREAD.getValue()))
        .collect(Collectors.toList());

    return models;
  }

  @Override
  public NotificationChannelResult sendNotificationWithResult(String deliveryKey, List<String> employeeIds, String title,
      String content, InternalMessageNotificationChannelOptions channelOptions) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new BusinessException("可靠站内消息发送需要无外层事务入口");
    }
    if (deliveryKey == null || deliveryKey.isBlank() || employeeIds == null || employeeIds.isEmpty()
        || employeeIds.stream().anyMatch(id -> id == null || id.isBlank()) || channelOptions == null) {
      return new NotificationChannelResult(NotificationChannelResult.Outcome.BLOCKED, "internal-message-input-invalid");
    }
    var recipients = employeeIds.stream().distinct().sorted().toList();
    var records = messages(recipients, title, content, channelOptions);
    var values = new LinkedHashMap<String, Object>();
    values.put("employees", recipients); values.put("title", title); values.put("content", content);
    values.put("platform", records.getFirst().getPlatform().isEmpty() ? List.of()
        : Arrays.stream(records.getFirst().getPlatform().split(",")).sorted().toList());
    values.put("type", channelOptions.getType()); values.put("action", channelOptions.getAction());
    String fingerprint = digest(canonical(values));
    String anchor = digest(List.of(deliveryKey));
    for (int index = 0; index < records.size(); index++) {
      var record = records.get(index); record.setId(index == 0 ? anchor : digest(List.of(deliveryKey, record.getEmployee())));
      record.setDeliveryKey(deliveryKey); record.setDeliveryFingerprint(fingerprint);
    }
    try { independently(model -> persist(model, anchor, deliveryKey, fingerprint, records)); }
    catch (RuntimeException failure) {
      if (!uniqueConflict(failure)) { throw failure; }
      // A concurrent complete collection may win the anchor. Its failed transaction must end before re-reading.
      independently(model -> { validateExisting(model, anchor, deliveryKey, fingerprint); return null; });
    }
    try { notifyUnreadCount(recipients); }
    catch (RuntimeException failure) {
      // The message is already committed; a websocket wakeup cannot revoke that receipt or create another message.
      logger.warn("Internal message unread wakeup failed after durable delivery: recipients={}", recipients.size(), failure);
    }
    return new NotificationChannelResult(NotificationChannelResult.Outcome.ACCEPTED, "internal-message-persisted");
  }

  private Void persist(TypedDataModel<String, NotificationInternalMessageModel> model, String anchor, String key,
      String fingerprint, List<NotificationInternalMessageModel> records) {
    var existing = model.get(anchor).getOrElseThrow(error -> new BusinessException("读取站内消息发送记录失败", error));
    if (existing != null) { validate(existing, key, fingerprint); return null; }
    model.batchInsert(records).getOrElseThrow(error -> new BusinessException("持久化站内消息失败", error));
    return null;
  }

  private void validateExisting(TypedDataModel<String, NotificationInternalMessageModel> model, String anchor, String key, String fingerprint) {
    var existing = model.get(anchor).getOrElseThrow(error -> new BusinessException("读取并发站内消息失败", error));
    if (existing == null) { throw new BusinessException("并发站内消息的持久结果不可见"); }
    validate(existing, key, fingerprint);
  }

  private void validate(NotificationInternalMessageModel existing, String key, String fingerprint) {
    if (!Objects.equals(existing.getDeliveryKey(), key) || !Objects.equals(existing.getDeliveryFingerprint(), fingerprint)) {
      throw new BusinessException("站内消息的稳定发送标识已绑定不同收件人或内容");
    }
  }

  private <T> T independently(java.util.function.Function<TypedDataModel<String, NotificationInternalMessageModel>, T> operation) {
    if (!(transactionManager instanceof DataSourceTransactionManager local) || local.getDataSource() == null) {
      throw new BusinessException("可靠站内消息需要可核验的本地事务资源");
    }
    var transaction = new TransactionTemplate(transactionManager); transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return DataModelExecutionContext.withPlatformWrite(() -> transaction.execute(status -> {
      var model = getDataModel(); var connection = DataSourceUtils.getConnection(local.getDataSource());
      try {
        if (model.getDataModel().getAdapter().requireTransactionResource().getOrElseThrow(error ->
            new BusinessException("站内消息事务资源不可用", error)) != connection) {
          throw new BusinessException("站内消息未参与配置的同一提交资源");
        }
        return operation.apply(model);
      } finally { DataSourceUtils.releaseConnection(connection, local.getDataSource()); }
    }));
  }

  private boolean uniqueConflict(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof UniqueConstraintViolationException) { return true; }
    }
    return false;
  }

  private Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>(); map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested))); return sorted;
    }
    if (value instanceof Collection<?> values) { return values.stream().map(this::canonical).toList(); }
    return value;
  }

  private String digest(Object value) {
    try {
      return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
          Json.toJsonString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("JVM 缺少 SHA-256", failure); }
  }

  @Override
  public Long unreadCount(String employee) {
    Map<String, Object> where = new HashMap<>();
    where.put("employee", employee);
    where.put("state", InternalMessageStateEnum.UNREAD.getValue());
    try {
      return getDataModel().count(where).getOrElseThrow(e -> e);
    } catch (Throwable e) {
      throw OuroborosExceptionMapper.toOuroborosException(e);
    }
  }

  @Override
  public void notifyUnreadCount(Collection<String> employeeIds) {
    employeeIds.parallelStream().forEach(employeeId ->
        employeeAggregator.getById(employeeId).map(Employee::getUser).ifPresent(user ->
            stompMessageSender.sendToUser(
                user,
                "/topic/notification/unread-count",
                Collections.singletonMap("unreadCount", unreadCount(employeeId))
            )
        )
    );
  }

  @Override
  public Boolean markReadByIds(String employee, Collection<String> ids) {
    Map<String, Object> where = new HashMap<>();
    where.put("employee", employee);
    where.put("id", new ArrayList<>(ids));
    where.put("state", InternalMessageStateEnum.UNREAD.getValue());

    Map<String, Object> data = new HashMap<>();
    data.put("state", InternalMessageStateEnum.READ.getValue());
    data.put("handleTime", LocalDateTime.now());

    try {
      var dataModel = getDataModel();
      var result = dataModel.update(where, data)
          .map(r -> r > 0)
          .onSuccess(r -> notifyUnreadCount(Collections.singletonList(employee)));
      return result.getOrElseThrow(e -> e);
    } catch (Throwable e) {
      throw OuroborosExceptionMapper.toOuroborosException(e);
    }
  }

  @Override
  public Boolean markReadByEmployee(String employee) {
    Map<String, Object> where = new HashMap<>();
    where.put("employee", employee);
    where.put("state", InternalMessageStateEnum.UNREAD.getValue());

    Map<String, Object> data = new HashMap<>();
    data.put("state", InternalMessageStateEnum.READ.getValue());
    data.put("handleTime", LocalDateTime.now());

    try {
      return getDataModel()
          .update(where, data)
          .map(r -> r > 0)
          .mapTry(b -> {
            if (b) {
              notifyUnreadCount(Collections.singletonList(employee));
            }
            return b;
          })
          .getOrElseThrow(e -> e);
    } catch (Throwable e) {
      throw OuroborosExceptionMapper.toOuroborosException(e);
    }
  }
}
