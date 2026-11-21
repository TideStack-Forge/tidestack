package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.uimodel.dev.model.LowcodeUIModel;

@Component
@Order(-1)
public class UIModelMetadataAdapter extends AbstractDevelopServiceAdapter<LowcodeUIModel> {

  public UIModelMetadataAdapter(DevelopService<LowcodeUIModel> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof LowcodeUIModel uiModel)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(uiModel.getPath());
    metadata.setLabel(uiModel.getTitle());
    metadata.setRawDetails(model);

    return metadata;
  }
}
