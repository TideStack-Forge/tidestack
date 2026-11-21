package com.ouroboros.workflow.approval.external.runtime.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时恢复外部动作事实。 */
@Component
public class ExternalActionRecoveryJob {

  private static final Logger logger = LoggerFactory.getLogger(ExternalActionRecoveryJob.class);
  private static final int BATCH_SIZE = 100;
  private static final int LEASE_SECONDS = 300;

  private final ExternalActionRecoveryService recoveryService;

  public ExternalActionRecoveryJob(ExternalActionRecoveryService recoveryService) {
    this.recoveryService = recoveryService;
  }

  @Scheduled(
      fixedDelayString = "${ouroboros.workflow.approval.external.action-recovery-delay-ms:30000}",
      initialDelayString = "${ouroboros.workflow.approval.external.action-recovery-delay-ms:30000}")
  public void recover() {
    int recovered = recoveryService.recoverDue(BATCH_SIZE, LEASE_SECONDS);
    if (recovered > 0) {
      logger.info("外部动作恢复完成: recovered={}", recovered);
    }
  }
}
