package com.ouroboros.data.initialization.runtime;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.initialization.InitializationTransaction;

public class SpringInitializationTransaction implements InitializationTransaction {
  private final TransactionTemplate transactionTemplate;

  public SpringInitializationTransaction(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
  }

  @Override
  public <T> T execute(Supplier<T> action) {
    return transactionTemplate.execute(status -> action.get());
  }
}
