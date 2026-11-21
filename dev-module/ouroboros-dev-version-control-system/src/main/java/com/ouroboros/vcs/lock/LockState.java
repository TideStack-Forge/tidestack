package com.ouroboros.vcs.lock;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LockState {
  UNLOCKED,
  LOCKED,
  OWNED;

  @JsonValue
  public static LockState fromValue(String value) {
    if (value == null) {
      return null;
    }
    switch (value.toUpperCase()) {
      case "UNLOCKED":
        return UNLOCKED;
      case "LOCKED":
        return LOCKED;
      case "OWNED":
        return OWNED;
      default:
        return null;
    }
  }
}
