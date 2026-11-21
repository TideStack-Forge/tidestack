package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.webapi.dev.model.LowcodeWebAPIModel;

@Component
@Order(-1)
public class WebAPIMetadataAdapter extends AbstractDevelopServiceAdapter<LowcodeWebAPIModel> {

  public WebAPIMetadataAdapter(DevelopService<LowcodeWebAPIModel> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof LowcodeWebAPIModel webapiModel)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(webapiModel.getApi());
    metadata.setLabel(webapiModel.getSummary());
    metadata.setRawDetails(webapiModel);

    return metadata;
  }
}
