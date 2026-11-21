package com.ouroboros.storage;

/** Technical session binding, used by upload coordination to lock and check its actual final key. */
public record MultipartSession(String key, Status status) {
  public enum Status { OPEN, COMPLETING, COMPLETED, ABORTED }
}
