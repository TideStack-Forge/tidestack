package com.ouroboros.workflow.approval.external.runtime.mapping;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 映射版本清理作业（Phase 4 M21-T3）：按保留窗口定期清理过期草稿/下线版本，
 * 绑定保护检查见 {@link MappingVersionCleanupService}。
 */
@Component
@ConditionalOnProperty(
    prefix = "ouroboros.workflow.approval.external.scheduling",
    name = "enabled",
    havingValue = "true")
public class MappingVersionCleanupJob {

  private static final Logger logger = LoggerFactory.getLogger(MappingVersionCleanupJob.class);

  private final MappingVersionCleanupService cleanupService;
  private final int draftRetentionDays;
  private final int retiredRetentionDays;

  @Autowired
  public MappingVersionCleanupJob(
      MappingVersionCleanupService cleanupService,
      @Value("${ouroboros.workflow.approval.external.mapping-draft-retention-days:30}")
          int draftRetentionDays,
      @Value("${ouroboros.workflow.approval.external.mapping-retired-retention-days:90}")
          int retiredRetentionDays) {
    this.cleanupService = cleanupService;
    this.draftRetentionDays = draftRetentionDays;
    this.retiredRetentionDays = retiredRetentionDays;
  }

  @Scheduled(
      fixedDelayString = "${ouroboros.workflow.approval.external.mapping-cleanup-delay-ms:86400000}",
      initialDelayString = "${ouroboros.workflow.approval.external.mapping-cleanup-delay-ms:86400000}")
  public void cleanup() {
    LocalDateTime now = LocalDateTime.now();
    MappingVersionCleanupService.CleanupReport report = cleanupService.cleanup(
        now.minusDays(draftRetentionDays), now.minusDays(retiredRetentionDays));
    logger.info("映射版本清理完成: 删除 {} 条，绑定保护跳过 {} 条",
        report.deleted(), report.skippedDueToBinding());
  }
}
