package com.ouroboros.app.cache;

import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.cache.CacheAdministration;
import com.ouroboros.cache.CacheEntryInspector;
import com.ouroboros.cache.CacheEntryTokenCodec;
import com.ouroboros.cache.ConcurrentMapCacheEntryInspector;
import com.ouroboros.cache.DefaultCacheEntryTokenCodec;

@Configuration
@ConditionalOnClass(name = "com.ouroboros.cache.management.service.PlatformCacheService")
@EnableConfigurationProperties(PlatformCacheTokenProperties.class)
public class PlatformCacheAdministrationConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CacheEntryTokenCodec cacheEntryTokenCodec(PlatformCacheTokenProperties properties) {
    return new DefaultCacheEntryTokenCodec(properties.requireSecret());
  }

  @Bean
  @ConditionalOnMissingBean(ConcurrentMapCacheEntryInspector.class)
  public ConcurrentMapCacheEntryInspector concurrentMapCacheEntryInspector() {
    return new ConcurrentMapCacheEntryInspector();
  }

  @Bean
  @ConditionalOnMissingBean
  public CacheAdministration cacheAdministration(CacheManager cacheManager,
                                                 ObjectProvider<CacheEntryInspector> inspectorProvider,
                                                 CacheEntryTokenCodec tokenCodec) {
    return new SpringCacheAdministration(
        cacheManager,
        inspectorProvider.orderedStream().collect(Collectors.toList()),
        tokenCodec
    );
  }
}
