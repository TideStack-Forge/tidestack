package com.ouroboros.dev.runtime.model;

public enum DebugSessionCloseReason {
  USER_CLOSED,
  EXPIRED,
  RUNTIME_RESTARTED,
  RUNTIME_DELETED,
  RUNTIME_GENERATION_CHANGED,
  FAILED
}
