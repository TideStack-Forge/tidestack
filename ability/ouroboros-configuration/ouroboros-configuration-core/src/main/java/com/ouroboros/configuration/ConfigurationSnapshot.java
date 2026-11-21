package com.ouroboros.configuration;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;

/** A single observation of a configuration value and its authoritative revision. */
public record ConfigurationSnapshot(String key, Object value, String storedRawValue,
                                    String revision, ConfigurationMetadata metadata) {
}
