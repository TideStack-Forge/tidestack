package com.ouroboros.notification.service;

import java.util.Collection;
import java.util.Optional;

import com.ouroboros.notification.constant.NotificationStateConstant;

import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;

/**
 * @author liansz
 */
public interface NotificationService {

  void add(NotificationModel model, Collection<NotificationStateModel> stateModel);

  /** Independently commits the complete, immutable source/rule sending intention before any network call. */
  NotificationDeliveryPlan.Snapshot prepareDelivery(NotificationDeliveryPlan plan);

  Optional<NotificationDeliveryPlan.Snapshot> findDelivery(String sourceFactId, String ruleIdentity);

  /** Claims one unit without retaining a database lock during the network operation. */
  Optional<NotificationDeliveryPlan.Claim> beginAttempt(String unitId);

  boolean completeAttempt(NotificationDeliveryPlan.Claim claim, NotificationStateConstant outcome, String diagnostic);

  /** Explicit evidence-based recovery; expiration alone never authorizes another send. */
  boolean resolveUnknown(String unitId, String token, NotificationStateConstant outcome, String operatorRef, String evidence);

  /** Clear terminal plan contents while keeping source/rule deduplication and minimal outcomes. */
  int clearExpiredDeliveryContents(int retentionDays, int batchSize);
}
