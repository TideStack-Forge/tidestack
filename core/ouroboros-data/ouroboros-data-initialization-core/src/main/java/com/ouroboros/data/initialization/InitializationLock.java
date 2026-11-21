package com.ouroboros.data.initialization;

import java.util.function.Supplier;

public interface InitializationLock {
  <T> T execute(String key, Supplier<T> action);
}
