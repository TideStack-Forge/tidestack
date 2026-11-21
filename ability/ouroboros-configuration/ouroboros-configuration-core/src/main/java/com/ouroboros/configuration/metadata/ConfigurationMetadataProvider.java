package com.ouroboros.configuration.metadata;

import java.util.List;

public interface ConfigurationMetadataProvider {

  List<ConfigurationMetadata> getAllConfigurationMetadata();
}
