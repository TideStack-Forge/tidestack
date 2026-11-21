package com.ouroboros.logicflow.dev.service;

/** Storage conflict, invalid source, capacity or expiry; mapped at the API boundary. */
public class DebugStoreException extends RuntimeException {
  private final boolean capacity;

  public DebugStoreException(String message) {
    this(message, false);
  }

  private DebugStoreException(String message, boolean capacity) {
    super(message);
    this.capacity = capacity;
  }

  public static DebugStoreException capacity(String message) {
    return new DebugStoreException(message, true);
  }

  public boolean isCapacity() {
    return capacity;
  }
}
