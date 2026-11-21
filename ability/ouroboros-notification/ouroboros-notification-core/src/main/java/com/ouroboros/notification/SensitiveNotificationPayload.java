package com.ouroboros.notification;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory delivery data which must never enter notification history or diagnostic logs. */
public final class SensitiveNotificationPayload extends LinkedHashMap<String, Object> {
  public SensitiveNotificationPayload(Map<String, Object> source) { super(source); }
  @Override public String toString() { return "SensitiveNotificationPayload[redacted]"; }
}
