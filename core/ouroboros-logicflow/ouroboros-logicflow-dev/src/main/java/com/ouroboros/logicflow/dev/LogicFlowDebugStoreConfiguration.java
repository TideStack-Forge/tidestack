package com.ouroboros.logicflow.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.logicflow.dev.service.CacheLogicFlowDebugEventStore;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore;

/** Default disposable recording adapter. A user-provided repository replaces it as a whole. */
@Configuration(proxyBeanMethods = false)
class LogicFlowDebugStoreConfiguration {
  @Bean
  @ConditionalOnMissingBean(LogicFlowDebugEventStore.class)
  LogicFlowDebugEventStore logicFlowDebugEventStore(
      @Value("${ouroboros.logicflow.debug.cache-region:logicflow-debug}") String region,
      @Value("${ouroboros.logicflow.debug.require-shared-cache:false}") boolean requireShared) {
    return new CacheLogicFlowDebugEventStore(CacheManager::getCacheManager, region, System::currentTimeMillis,
        CacheLogicFlowDebugEventStore.Limits.defaults(), requireShared);
  }
}
