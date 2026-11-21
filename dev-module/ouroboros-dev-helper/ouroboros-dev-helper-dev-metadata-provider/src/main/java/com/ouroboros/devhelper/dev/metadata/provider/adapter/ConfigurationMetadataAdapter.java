package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;

@Component
@Order(-1)
public class ConfigurationMetadataAdapter extends AbstractDevelopServiceAdapter<ConfigurationMetadata> {

  public ConfigurationMetadataAdapter(DevelopService<ConfigurationMetadata> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof ConfigurationMetadata configurationMetadata)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(configurationMetadata.getKey());
    metadata.setLabel(configurationMetadata.getDescription());
    metadata.setRawDetails(configurationMetadata);

    return metadata;
  }
}
