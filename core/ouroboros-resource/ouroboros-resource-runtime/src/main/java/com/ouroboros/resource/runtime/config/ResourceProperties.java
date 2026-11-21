package com.ouroboros.resource.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for resource management
 */
@ConfigurationProperties(prefix = "ouroboros.resource")
public class ResourceProperties {

    /**
     * Base directory for app default resources on filesystem.
     */
    private String appDefaultBaseDirectory = "resources";

    /**
     * Base directory for runtime override resources.
     */
    private String runtimeOverrideBaseDirectory = "data/resources";

    public String getAppDefaultBaseDirectory() {
        return appDefaultBaseDirectory;
    }

    public void setAppDefaultBaseDirectory(String appDefaultBaseDirectory) {
        this.appDefaultBaseDirectory = appDefaultBaseDirectory;
    }

    public String getRuntimeOverrideBaseDirectory() {
        return runtimeOverrideBaseDirectory;
    }

    public void setRuntimeOverrideBaseDirectory(String runtimeOverrideBaseDirectory) {
        this.runtimeOverrideBaseDirectory = runtimeOverrideBaseDirectory;
    }
}
