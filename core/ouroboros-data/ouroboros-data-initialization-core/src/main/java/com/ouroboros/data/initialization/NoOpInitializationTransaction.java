package com.ouroboros.data.initialization;

import java.util.function.Supplier;

public class NoOpInitializationTransaction implements InitializationTransaction {
  @Override
  public <T> T execute(Supplier<T> action) {
    return action.get();
  }
}
