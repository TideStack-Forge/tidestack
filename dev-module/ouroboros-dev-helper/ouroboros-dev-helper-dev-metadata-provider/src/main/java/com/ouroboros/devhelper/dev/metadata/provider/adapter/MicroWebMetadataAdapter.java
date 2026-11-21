package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.uimodel.dev.microweb.model.LowCodeMicroWebModel;
import com.ouroboros.modelmanage.DevelopService;

@Component
@Order(-1)
public class MicroWebMetadataAdapter extends AbstractDevelopServiceAdapter<LowCodeMicroWebModel> {

  public MicroWebMetadataAdapter(DevelopService<LowCodeMicroWebModel> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof LowCodeMicroWebModel microWebModel)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(microWebModel.getName());
    metadata.setLabel(microWebModel.getDescription());
    metadata.setRawDetails(microWebModel);

    return metadata;
  }
}
