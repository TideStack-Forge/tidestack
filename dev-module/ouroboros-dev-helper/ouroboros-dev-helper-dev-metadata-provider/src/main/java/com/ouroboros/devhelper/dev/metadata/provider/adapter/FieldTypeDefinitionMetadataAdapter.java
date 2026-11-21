package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;

@Component
@Order(-1)
public class FieldTypeDefinitionMetadataAdapter extends AbstractDevelopServiceAdapter<Map<String, Object>> {

  public FieldTypeDefinitionMetadataAdapter(@Qualifier("fieldTypeManagerServiceImpl") DevelopService<Map<String, Object>> developService) {
    super(developService);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected Metadata convert(Object data) {
    if (!(data instanceof Map<?, ?> model)) {
      return null;
    }

    var metadata = new Metadata();
    metadata.setName((String) model.get("name"));
    metadata.setLabel((String) model.get("label"));
    metadata.setDetails((Map<String, Object>) model);

    return metadata;
  }
}
