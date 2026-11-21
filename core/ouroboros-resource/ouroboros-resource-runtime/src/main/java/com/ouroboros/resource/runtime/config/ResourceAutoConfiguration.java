package com.ouroboros.resource.runtime.config;

import com.ouroboros.resource.runtime.provider.*;
import com.ouroboros.resource.spi.ResourceService;
import com.ouroboros.resource.provider.ResourceProvider;
import com.ouroboros.resource.provider.ResourceOverrideProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Auto-configuration for resource management
 */
@Configuration
@EnableConfigurationProperties(ResourceProperties.class)
public class ResourceAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ResourceAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "appDefaultResourceProvider")
    public AppDefaultResourceProvider appDefaultResourceProvider(ResourceProperties properties) {
        logger.info("Creating AppDefaultResourceProvider");
        return new AppDefaultResourceProvider(properties.getAppDefaultBaseDirectory());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "runtimeOverrideResourceProvider")
    public RuntimeOverrideResourceProvider runtimeOverrideResourceProvider(ResourceProperties properties, org.springframework.core.env.Environment environment) {
        logger.info("Creating RuntimeOverrideResourceProvider");
        return new RuntimeOverrideResourceProvider(com.ouroboros.storage.runtime.StorageBinding.create(environment,
            "ouroboros.resource.runtime-override.storage",
            () -> com.ouroboros.storage.runtime.StorageConfiguration.filesystem(properties.getRuntimeOverrideBaseDirectory()),
            "ouroboros.resource.runtime-override-base-directory"));
    }

    @Bean
    @ConditionalOnMissingBean(name = "compositeResourceProvider")
    public CompositeResourceProvider compositeResourceProvider(
        @Qualifier("appDefaultResourceProvider") ResourceProvider appDefaultResourceProvider,
        @Qualifier("runtimeOverrideResourceProvider") ResourceOverrideProvider runtimeOverrideResourceProvider
    ) {
        logger.info("Creating CompositeResourceProvider");
        return new CompositeResourceProvider(
            Arrays.asList(runtimeOverrideResourceProvider, appDefaultResourceProvider));
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceService resourceService(
        @Qualifier("runtimeOverrideResourceProvider") ResourceOverrideProvider runtimeOverrideResourceProvider,
        @Qualifier("appDefaultResourceProvider") ResourceProvider appDefaultResourceProvider
    ) {
        logger.info("Creating DefaultResourceService");
        return new DefaultResourceService(runtimeOverrideResourceProvider, appDefaultResourceProvider);
    }
}
