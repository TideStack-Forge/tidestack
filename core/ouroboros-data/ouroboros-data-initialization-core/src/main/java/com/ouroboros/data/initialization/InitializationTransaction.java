package com.ouroboros.data.initialization;

import java.util.function.Supplier;

public interface InitializationTransaction {
  <T> T execute(Supplier<T> action);
}
