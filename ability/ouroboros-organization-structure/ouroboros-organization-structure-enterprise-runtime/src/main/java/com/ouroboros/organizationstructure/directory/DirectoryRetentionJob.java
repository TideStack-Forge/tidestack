package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.data.model.DataModelExecutionContext;

/** Bounded cleanup of directory-owned previews; bindings and platform employees are never removed. */
public final class DirectoryRetentionJob {
  private static final Logger LOG = LoggerFactory.getLogger(DirectoryRetentionJob.class);
  private final DirectoryStore store;
  private final ConfigurationService configuration;

  public DirectoryRetentionJob(DirectoryStore store, ConfigurationService configuration) {
    this.store = store; this.configuration = configuration;
  }

  @Scheduled(fixedDelayString = "${ouroboros.organization.directory.cleanup-delay-ms:3600000}",
      initialDelayString = "${ouroboros.organization.directory.cleanup-delay-ms:3600000}")
  public void clearExpiredContents() {
    int snapshots = configuration.getOrDefault(Integer.class, "organization.directory.snapshot-retention-days", 7);
    int details = configuration.getOrDefault(Integer.class, "organization.directory.detail-retention-days", 30);
    long cleared = DataModelExecutionContext.withPlatformWrite(() -> store.clearExpiredRunContents(LocalDateTime.now(), snapshots, details));
    LOG.info("目录同步内容清理完成: updated={}", cleared);
  }
}
