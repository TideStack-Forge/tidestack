package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;

@Component
@Order(-1)
public class UISchemaMetadataAdapter extends AbstractDevelopServiceAdapter<SchemaNodeWrapper> {

  public UISchemaMetadataAdapter(DevelopService<SchemaNodeWrapper> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof SchemaNodeWrapper schemaNodeWrapper)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(schemaNodeWrapper.getName());
    metadata.setLabel(schemaNodeWrapper.getTitle());
    metadata.setRawDetails(schemaNodeWrapper.getSchema());

    return metadata;
  }
}
