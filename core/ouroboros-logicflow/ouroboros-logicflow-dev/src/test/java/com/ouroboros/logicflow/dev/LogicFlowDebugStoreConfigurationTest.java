package com.ouroboros.logicflow.dev;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.logicflow.dev.service.CacheLogicFlowDebugEventStore;
import com.ouroboros.logicflow.dev.service.DebugStoreException;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore;

class LogicFlowDebugStoreConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(LogicFlowDebugStoreConfiguration.class);

  @Test
  void defaultRepositoryUsesOfficialCacheManagerAndSingleInstanceCapabilities() {
    context.run(beans -> {
      assertEquals(1, beans.getBeansOfType(LogicFlowDebugEventStore.class).size());
      var repository = beans.getBean(LogicFlowDebugEventStore.class);
      assertInstanceOf(CacheLogicFlowDebugEventStore.class, repository);
      assertTrue(repository.capabilities().atomicControl());
      assertFalse(repository.capabilities().durableHistory());
    });
  }

  @Test
  void customRepositoryCompletelyReplacesDefaultCacheAdapter() {
    var custom = mock(LogicFlowDebugEventStore.class);
    context.withBean("customDebugRepository", LogicFlowDebugEventStore.class, () -> custom).run(beans -> {
      assertEquals(1, beans.getBeansOfType(LogicFlowDebugEventStore.class).size());
      assertSame(custom, beans.getBean(LogicFlowDebugEventStore.class));
      assertTrue(beans.getBeansOfType(CacheLogicFlowDebugEventStore.class).isEmpty());
    });
  }

  @Test
  void sharedModeFailsClosedWhenConfiguredCacheIsOnlyLocalMemory() {
    context.withPropertyValues("ouroboros.logicflow.debug.require-shared-cache=true").run(beans ->
        assertThrows(DebugStoreException.class, () -> beans.getBean(LogicFlowDebugEventStore.class).capabilities()));
  }
}
