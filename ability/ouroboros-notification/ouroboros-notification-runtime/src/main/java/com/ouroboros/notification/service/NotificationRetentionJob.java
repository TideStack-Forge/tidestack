package com.ouroboros.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ouroboros.configuration.ConfigurationService;

/** Uses the existing independently committed notification plan path. */
@Component
public final class NotificationRetentionJob {
  private static final Logger LOG = LoggerFactory.getLogger(NotificationRetentionJob.class);
  private final NotificationService notifications;
  private final ConfigurationService configuration;

  public NotificationRetentionJob(NotificationService notifications, ConfigurationService configuration) {
    this.notifications = notifications; this.configuration = configuration;
  }

  @Scheduled(fixedDelayString = "${ouroboros.notification.cleanup-delay-ms:3600000}",
      initialDelayString = "${ouroboros.notification.cleanup-delay-ms:3600000}")
  public void clearExpiredContents() {
    int days = configuration.getOrDefault(Integer.class, "notification.delivery.content-retention-days", 30);
    LOG.info("通知计划内容清理完成: cleared={}", notifications.clearExpiredDeliveryContents(days, 100));
  }
}
