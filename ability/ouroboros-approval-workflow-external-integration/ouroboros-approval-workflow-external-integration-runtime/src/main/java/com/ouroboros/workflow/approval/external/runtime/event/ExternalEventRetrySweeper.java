package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 收件箱兜底扫描：after-commit 触发丢失或处理失败的条目按策略重投。
 * 重试与死信规则见 {@link ExternalEventProcessor}。
 */
@Component
@ConditionalOnProperty(
    prefix = "ouroboros.workflow.approval.external.scheduling",
    name = "enabled",
    havingValue = "true")
public class ExternalEventRetrySweeper {

  private static final Logger logger = LoggerFactory.getLogger(ExternalEventRetrySweeper.class);
  private static final int BATCH_SIZE = ExternalEventInboxService.DEFAULT_RECOVERY_BATCH_SIZE;

  private final ExternalEventInboxService inboxService;
  private final ExternalEventProcessor processor;

  @Autowired
  public ExternalEventRetrySweeper(
      ExternalEventInboxService inboxService,
      ExternalEventProcessor processor) {
    this.inboxService = inboxService;
    this.processor = processor;
  }

  @Scheduled(
      fixedDelayString = "${ouroboros.workflow.approval.external.retry-sweep-delay-ms:30000}",
      initialDelayString = "${ouroboros.workflow.approval.external.retry-sweep-delay-ms:30000}")
  public void sweep() {
    int scanned = 0;
    LocalDateTime now = LocalDateTime.now();
    // 1) PROCESSING 租约过期后由 processor 生成新 claim token 重领。
    List<ExternalEventInbox> expiredProcessing = inboxService.findExpiredProcessing(now);
    for (ExternalEventInbox row : expiredProcessing) {
      if (scanned >= BATCH_SIZE) {
        return;
      }
      reingest(row);
      scanned++;
    }
    // 2) FAILED 未超上限条目重试（先重置为 PENDING 再重投）。重试优先于陈旧 PENDING，避免积压饿死。
    List<ExternalEventInbox> failed = inboxService.findFailedRetryable(now);
    for (ExternalEventInbox row : failed) {
      if (scanned >= BATCH_SIZE) {
        return;
      }
      var reset = inboxService.requeueAutomatically(row.getId(), now);
      if (reset.isSuccess()) {
        reingest(row);
        scanned++;
      }
    }
    // 3) PENDING 由受控 worker 领取；不依赖 HTTP after-commit 回调是否执行。
    List<ExternalEventInbox> pendingStale = inboxService.findPendingOlderThan(now);
    for (ExternalEventInbox row : pendingStale) {
      if (scanned >= BATCH_SIZE) {
        return;
      }
      reingest(row);
      scanned++;
    }
  }

  private void reingest(ExternalEventInbox row) {
    var result = processor.processRow(row);
    if (result.isFailure()) {
      logger.debug("收件箱条目重投失败: inboxId={}, cause={}", row.getId(), result.getCause().getMessage());
    }
  }
}
