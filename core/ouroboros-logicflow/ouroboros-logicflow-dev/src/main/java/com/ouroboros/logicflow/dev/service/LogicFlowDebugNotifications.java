package com.ouroboros.logicflow.dev.service;

/** Best-effort hints; clients always recover from the authoritative recording store. */
public interface LogicFlowDebugNotifications {
  void updated(String owner, String captureId, long receivedThrough);
}
