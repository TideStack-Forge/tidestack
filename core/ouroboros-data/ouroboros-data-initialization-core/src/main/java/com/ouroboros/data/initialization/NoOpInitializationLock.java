package com.ouroboros.data.initialization;

import java.util.function.Supplier;

public class NoOpInitializationLock implements InitializationLock {
  @Override
  public <T> T execute(String key, Supplier<T> action) {
    return action.get();
  }
}
