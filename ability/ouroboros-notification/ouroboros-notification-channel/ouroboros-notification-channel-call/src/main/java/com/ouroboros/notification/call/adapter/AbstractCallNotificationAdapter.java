package com.ouroboros.notification.call.adapter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.call.center.CallNotificationAdapterCenter;
import com.ouroboros.notification.call.enums.CallState;
import com.ouroboros.notification.call.event.CallNotificationStateChangedEvent;
import com.ouroboros.notification.call.service.CallNotificationRecordService;
import com.ouroboros.notification.call.service.model.CallNotificationRecordModel;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
public abstract class AbstractCallNotificationAdapter<D extends NotificationChannelSchema, O extends NotificationChannelOptions>
    implements NotificationAdapter<D, O> {

  private static final Logger logger = LoggerFactory.getLogger(AbstractCallNotificationAdapter.class);

  @Autowired
  private CallNotificationRecordService callNotificationRecordService;

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void send(Collection<String> addresses, D channelSchema, Map<String, Object> payload, O channelOptions) throws NotificationSendException {
    if (CollectionUtils.isEmpty(addresses)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY);
    }

    validateChannelSchema(channelSchema);

    String provider = CallNotificationAdapterCenter.getProvider(this)
        .orElseThrow(() -> new IllegalStateException("call notification adapter channel not found: " + getClass().getName()));

    List<CallNotificationRecordModel> records = new ArrayList<>();
    List<String> failureMessages = new ArrayList<>();

    for (String address : addresses) {
      CallNotificationRecordModel model = new CallNotificationRecordModel();
      model.setProvider(provider);
      model.setPhone(address);
      model.setBusinessType(resolveBusinessType(payload));
      model.setBusinessId(resolveBusinessId(payload));
      model.setState(CallState.PENDING.name());
      model.setSendTime(LocalDateTime.now());

      try {
        Map<String, Object> receipt = sendAndCollect(address, channelSchema, payload, channelOptions);
        if (receipt == null) {
          throw new NotificationSendException(NotificationSendException.ErrorDefine.SEND_RESPONSE_ERROR, "call dispatch receipt is null");
        }

        model.setReceipt(Json.toJsonString(receipt));
      } catch (Exception e) {
        failureMessages.add(address + ": " + e.getMessage());
        model.setState(CallState.FAIL.name());
        model.setFinishTime(LocalDateTime.now());
      } finally {
        records.add(model);
      }
    }

    if (!records.isEmpty()) {
      try {
        callNotificationRecordService.recordDispatch(records);

        for (CallNotificationRecordModel record : records) {
          if (!Objects.equals(record.getState(), CallState.FAIL.name())) {
            continue;
          }
          try {
            publishStateChangedEventAfterCompletion(
                new CallNotificationStateChangedEvent(this,
                    record.getPhone(), record.getBusinessType(), record.getBusinessId(),
                    CallState.FAIL, record.getSendTime(), record.getFinishTime()
                )
            );
          } catch (Exception e) {
            logger.warn("publish call notification failure event failed, recordId={}", record.getId(), e);
          }
        }
      } catch (Exception e) {
        throw new NotificationSendException(NotificationSendException.ErrorDefine.OTHER, e.getMessage());
      }
    }

    if (!failureMessages.isEmpty()) {
      throw new NotificationSendException(
          NotificationSendException.ErrorDefine.SEND_RESPONSE_ERROR,
          failureMessages.toArray(new String[0])
      );
    }
  }

  /**
   * Publishes provider failure events after the surrounding transaction completes.
   * The event is emitted after both commit and rollback because a provider failure
   * must not be lost when the caller transaction rolls back.
   */
  private void publishStateChangedEventAfterCompletion(Object event) {
    if (event == null) {
      return;
    }

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      publishStateChangedEventSafely(event);
      return;
    }

    try {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          publishStateChangedEventSafely(event);
        }
      });
    } catch (RuntimeException ex) {
      logger.warn("register call notification event after completion failed, eventType={}",
          event.getClass().getName(), ex);
      publishStateChangedEventSafely(event);
    }
  }

  private void publishStateChangedEventSafely(Object event) {
    try {
      applicationEventPublisher.publishEvent(event);
    } catch (RuntimeException ex) {
      logger.error("publish call notification event failed, eventType={}",
          event.getClass().getName(), ex);
    }
  }

  @Override
  public Class<? extends D> getChannelSchemaClass() {
    return resolveActualTypeArgument(0);
  }

  @Override
  public Class<? extends O> getChannelOptionsClass() {
    return resolveActualTypeArgument(1);
  }

  protected abstract Map<String, Object> sendAndCollect(String address, D channelSchema,
                                                        Map<String, Object> payload, O channelOptions);

  public abstract CallStateQueryResult query(Map<String, Object> receipt);

  protected void validateChannelSchema(D channelSchema) {
    if (channelSchema == null) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.TEMPLATE_CHANNEL_EMPTY);
    }
  }

  protected String resolveBusinessType(Map<String, Object> payload) {
    return Maps.getAs(payload, "businessType", String.class).orElse(null);
  }

  protected String resolveBusinessId(Map<String, Object> payload) {
    return Maps.getAs(payload, "businessId", String.class).orElse(null);
  }

  private <T> Class<? extends T> resolveActualTypeArgument(int index) {
    Type genericSuperclass = getClass().getGenericSuperclass();
    while (!(genericSuperclass instanceof ParameterizedType) && genericSuperclass != null) {
      if (!(genericSuperclass instanceof Class<?>)) {
        genericSuperclass = null;
        break;
      }
      genericSuperclass = ((Class<?>) genericSuperclass).getGenericSuperclass();
    }
    if (genericSuperclass == null) {
      throw new InternalException("获取 call 渠道泛型类型失败", null);
    }

    Type type = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[index];
    if (type instanceof Class<?>) {
      return (Class<? extends T>) type;
    }
    throw new InternalException("获取 call 渠道泛型类型失败", null);
  }
}
