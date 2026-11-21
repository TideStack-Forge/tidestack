package com.ouroboros.resource.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for application resource development.
 */
@ComponentScan
@Configuration
public class ResourceDevAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ResourceDevAutoConfiguration.class);

    @PostConstruct
    void init() {
        logger.info("Ouroboros Resource Development Auto Configuration loaded");
    }
}
