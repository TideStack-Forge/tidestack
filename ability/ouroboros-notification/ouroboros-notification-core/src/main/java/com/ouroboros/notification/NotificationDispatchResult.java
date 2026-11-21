package com.ouroboros.notification;

import java.util.List;

import com.ouroboros.notification.constant.NotificationStateConstant;

/** Contains operational identities and outcomes, never the notification payload, addresses or attempt tokens. */
public record NotificationDispatchResult(String notificationId, Status status, String diagnostic, List<Unit> units) {
  public enum Status { COMPLETE, PENDING, BLOCKED, UNKNOWN }

  public NotificationDispatchResult { units = List.copyOf(units); }

  public boolean isComplete() { return status == Status.COMPLETE; }

  public record Unit(String id, String recipientId, String channel, NotificationStateConstant state, String diagnostic) { }
}
