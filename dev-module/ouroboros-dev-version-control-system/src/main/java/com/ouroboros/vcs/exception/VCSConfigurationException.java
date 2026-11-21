package com.ouroboros.vcs.exception;

public class VCSConfigurationException extends VCSException {
    private final String configKey;
    private final String configValue;

    public VCSConfigurationException(VCSErrorCode errorCode, String configKey, String configValue) {
        super(errorCode, configKey, configValue);
        this.configKey = configKey;
        this.configValue = configValue;
    }

    public VCSConfigurationException(VCSErrorCode errorCode, Throwable cause, String configKey, String configValue) {
        super(errorCode, cause, configKey, configValue);
        this.configKey = configKey;
        this.configValue = configValue;
    }

    public static VCSConfigurationException invalidConfiguration(String configKey, String configValue) {
        return new VCSConfigurationException(VCSErrorCode.INVALID_CONFIGURATION, configKey, configValue);
    }

    public static VCSConfigurationException repositoryPathInvalid(String repositoryPath) {
        return new VCSConfigurationException(VCSErrorCode.REPOSITORY_PATH_INVALID, "repositoryPath", repositoryPath);
    }

    public static VCSConfigurationException userNotConfigured(String userInfo) {
        return new VCSConfigurationException(VCSErrorCode.USER_NOT_CONFIGURED, "user", userInfo);
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValue() {
        return configValue;
    }
}