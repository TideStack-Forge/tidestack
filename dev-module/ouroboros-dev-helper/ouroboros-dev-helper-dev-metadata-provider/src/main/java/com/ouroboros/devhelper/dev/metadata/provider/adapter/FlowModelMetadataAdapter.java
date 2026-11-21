package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Component
@Order(-1)
public class FlowModelMetadataAdapter extends AbstractDevelopServiceAdapter<FlowModelMeta> {

  public FlowModelMetadataAdapter(DevelopService<FlowModelMeta> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object data) {
    if (!(data instanceof ModelFileMetaWrapper wrapper)) {
      return null;
    }

    FlowModelMeta model = (FlowModelMeta) wrapper.getModel();
    var metadata = new LazyMetadata();
    metadata.setName(model.getName());
    metadata.setLabel(model.getDescription());
    metadata.setRawDetails(model);

    return metadata;
  }

}
