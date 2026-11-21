package com.ouroboros.notification.call.scheduler;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ouroboros.notification.call.center.CallNotificationAdapterCenter;
import com.ouroboros.notification.call.adapter.AbstractCallNotificationAdapter;
import com.ouroboros.notification.call.event.CallNotificationStateChangedEvent;
import com.ouroboros.notification.call.enums.CallState;
import com.ouroboros.notification.call.adapter.CallStateQueryResult;
import com.ouroboros.notification.call.service.CallNotificationRecordService;
import com.ouroboros.notification.call.service.model.CallNotificationRecordModel;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Component
public class CallNotificationStatePoller {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final CallNotificationRecordService callNotificationRecordService;

  private final ApplicationEventPublisher applicationEventPublisher;

  private final int batchSize;

  public CallNotificationStatePoller(CallNotificationRecordService callNotificationRecordService,
                                     ApplicationEventPublisher applicationEventPublisher,
                                     @Value("${ouroboros.notification.call.state-poll.batch-size:100}") int batchSize) {
    this.callNotificationRecordService = callNotificationRecordService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${ouroboros.notification.call.state-poll.fixed-delay-ms:30000}", initialDelay = 60000)
  public void pollPendingStates() {
    List<? extends CallNotificationRecordModel> records =
        callNotificationRecordService.searchPendingRecords(batchSize);
    for (CallNotificationRecordModel record : records) {
      pollSingleRecord(record);
    }
  }

  private void pollSingleRecord(CallNotificationRecordModel record) {
    if (record == null || ObjectUtils.isEmpty(record.getId())) {
      return;
    }

    AbstractCallNotificationAdapter<?, ?> adapter = CallNotificationAdapterCenter.getAllAdapter().get(record.getProvider());
    if (adapter == null) {
      logger.warn("call state query adapter not found, recordId={}, provider={}", record.getId(), record.getProvider());
      return;
    }

    try {
      if (record.getReceipt() == null || record.getReceipt().isEmpty()) {
        logger.warn("call receipt is empty, recordId={}, provider={}", record.getId(), record.getProvider());
        return;
      }

      Map<String, Object> receipt = Json.tryToMap(record.getReceipt())
          .getOrElse(Collections.emptyMap());
      if (receipt.isEmpty()) {
        logger.warn("call receipt is invalid, recordId={}, provider={}", record.getId(), record.getProvider());
        return;
      }

      CallStateQueryResult result = adapter.query(receipt);
      if (result == null) {
        return;
      }

      if (shouldPublishStateChangedEvent(result.getState(), record.getSendTime(), LocalDateTime.now())) {
        CallNotificationRecordModel model = callNotificationRecordService.applyQueryResult(record.getId(), result);

        applicationEventPublisher.publishEvent(
            new CallNotificationStateChangedEvent(this,
                model.getPhone(), model.getBusinessType(), model.getBusinessId(),
                CallState.valueOf(model.getState()), model.getSendTime(), model.getFinishTime()
            )
        );
      }
    } catch (Exception e) {
      logger.warn("query call state failed, recordId={}, provider={}", record.getId(), record.getProvider(), e);
    }
  }

  static boolean shouldPublishStateChangedEvent(CallState state, LocalDateTime sendTime, LocalDateTime now) {
    if (state == null || !state.isFinished()) {
      return false;
    }
    if (state == CallState.CONNECTED) {
      return true;
    }
    return sendTime != null && now.isAfter(sendTime.plusMinutes(1));
  }
}
