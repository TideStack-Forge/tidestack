package com.ouroboros.data.initialization.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SpringInitializationTransactionTest {
  @Test
  void commitsWhenActionSucceeds() {
    var manager = new RecordingTransactionManager();
    var transaction = new SpringInitializationTransaction(manager);

    String result = transaction.execute(() -> "ok");

    assertEquals("ok", result);
    assertEquals(1, manager.transactions);
    assertEquals(1, manager.commits);
    assertEquals(0, manager.rollbacks);
  }

  @Test
  void rollsBackWhenActionFails() {
    var manager = new RecordingTransactionManager();
    var transaction = new SpringInitializationTransaction(manager);

    assertThrows(IllegalStateException.class, () -> transaction.execute(() -> {
      throw new IllegalStateException("boom");
    }));

    assertEquals(1, manager.transactions);
    assertEquals(0, manager.commits);
    assertEquals(1, manager.rollbacks);
  }

  private static final class RecordingTransactionManager implements PlatformTransactionManager {
    private int transactions;
    private int commits;
    private int rollbacks;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      transactions++;
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      commits++;
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbacks++;
    }
  }
}
