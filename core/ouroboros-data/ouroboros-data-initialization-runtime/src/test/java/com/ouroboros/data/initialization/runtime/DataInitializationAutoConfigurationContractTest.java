package com.ouroboros.data.initialization.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.data.TypedDataModelAutoConfiguration;
import com.ouroboros.data.initialization.DataInitializationReconciler;
import com.ouroboros.data.initialization.InitializationLedger;
import com.ouroboros.data.initialization.InitializationLock;
import com.ouroboros.data.initialization.InitializationTransaction;
import com.ouroboros.data.initialization.NoOpInitializationTransaction;
import com.ouroboros.data.initialization.TargetDatasetGateway;
import com.ouroboros.data.initialization.TargetDatasetRow;
import com.ouroboros.data.initialization.runtime.model.DataInitializationLedgerRecord;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.lock.DistributedLock;
import com.ouroboros.lock.DistributedLockManager;

class DataInitializationAutoConfigurationContractTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(DataInitializationAutoConfiguration.class);

  // Auto-configuration loading is covered by runtimeRequiresTransactionManagerByDefault.

  @Test
  void autoConfigurationScansLedgerModels() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class, DataInitializationAutoConfiguration.class,
          TestTransactionConfig.class);
      context.refresh();

      assertThat(context.getBeanNamesForType(ResolvableType.forClassWithGenerics(
          TypedDataModel.class, String.class, DataInitializationLedgerRecord.class))).hasSize(1);
    }
  }

  // Lifecycle ordering is covered by OuroborosDataModelLifecycleAdapterTest's event ordering check.

  @Test
  void defaultRuntimeBeansCanBeReplacedByApplications() {
    contextRunner.withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withBean(TargetDatasetGateway.class, TestTargetDatasetGateway::new)
        .run(context -> assertThat(context.getBean(TargetDatasetGateway.class))
            .isInstanceOf(TestTargetDatasetGateway.class));
  }

  @Test
  void runtimeRequiresTransactionManagerByDefault() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void customTransactionAllowsContextWithoutPlatformTransactionManager() {
    contextRunner.withBean(InitializationTransaction.class, NoOpInitializationTransaction::new)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(DataInitializationReconciler.class);
        });
  }

  @Test
  void distributedInitializationLockUsesSpringManagedLockManager() {
    contextRunner.withBean(InitializationTransaction.class, NoOpInitializationTransaction::new)
        .withBean(RecordingDistributedLockManager.class, RecordingDistributedLockManager::new)
        .run(context -> {
          var manager = context.getBean(RecordingDistributedLockManager.class);

          String result = context.getBean(InitializationLock.class).execute("data-init-test", () -> "ok");

          assertEquals("ok", result);
          assertEquals("data-init-test", manager.lockKey);
          assertTrue(manager.lock.locked);
          assertTrue(manager.lock.unlocked);
        });
  }

  private static final class RecordingDistributedLockManager implements DistributedLockManager {
    private final RecordingDistributedLock lock = new RecordingDistributedLock();
    private String lockKey;

    @Override
    public DistributedLock getLock(String key) {
      lockKey = key;
      return lock;
    }
  }

  private static final class RecordingDistributedLock implements DistributedLock {
    private boolean locked;
    private boolean unlocked;

    @Override
    public void lock() {
      locked = true;
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
      locked = true;
    }

    @Override
    public boolean tryLock() {
      locked = true;
      return true;
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) {
      locked = true;
      return true;
    }

    @Override
    public void unlock() {
      unlocked = true;
    }

    @Override
    public boolean isLocked() {
      return locked && !unlocked;
    }
  }

  private static final class TestTargetDatasetGateway implements TargetDatasetGateway {
    @Override
    public Optional<TargetDatasetRow> findOne(String targetModel, Map<String, Object> matchValues) {
      return Optional.empty();
    }

    @Override
    public void insert(String targetModel, Map<String, Object> values) {
      throw new UnsupportedOperationException("test gateway does not persist rows");
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class TestTransactionConfig {
    @Bean
    PlatformTransactionManager platformTransactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }
}
