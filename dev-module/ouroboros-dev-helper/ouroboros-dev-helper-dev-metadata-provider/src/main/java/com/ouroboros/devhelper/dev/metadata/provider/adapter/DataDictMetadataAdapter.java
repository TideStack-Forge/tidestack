package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;

@Component
@Order(-1)
public class DataDictMetadataAdapter extends AbstractDevelopServiceAdapter<DataDict> {

  public DataDictMetadataAdapter(DevelopService<DataDict> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof DataDict dataDict)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(dataDict.getName());
    metadata.setLabel(dataDict.getTitle());
    metadata.setRawDetails(dataDict);

    return metadata;
  }
}
