package com.ouroboros.workflow.approval.external.runtime.start;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时领取并恢复可安全推进的外部启动事实。 */
@Component
public class ExternalStartRecoveryJob {

  private static final Logger logger = LoggerFactory.getLogger(ExternalStartRecoveryJob.class);
  private static final int BATCH_SIZE = 100;
  private static final int LEASE_SECONDS = 300;

  private final ExternalStartRecoveryService recoveryService;

  public ExternalStartRecoveryJob(ExternalStartRecoveryService recoveryService) {
    this.recoveryService = recoveryService;
  }

  @Scheduled(
      fixedDelayString = "${ouroboros.workflow.approval.external.start-recovery-delay-ms:30000}",
      initialDelayString = "${ouroboros.workflow.approval.external.start-recovery-delay-ms:30000}")
  public void recover() {
    int recovered = recoveryService.recoverDue(BATCH_SIZE, LEASE_SECONDS);
    if (recovered > 0) {
      logger.info("外部启动恢复完成: recovered={}", recovered);
    }
  }
}
