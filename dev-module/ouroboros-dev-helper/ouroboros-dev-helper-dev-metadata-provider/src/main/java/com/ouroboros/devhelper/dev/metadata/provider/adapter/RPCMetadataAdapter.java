package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.rpc.metadata.RPCModelMetadata;

@Component
@Order(-1)
public class RPCMetadataAdapter extends AbstractDevelopServiceAdapter<RPCModelMetadata> {

  public RPCMetadataAdapter(DevelopService<RPCModelMetadata> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof RPCModelMetadata rpcModelMetadata)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(rpcModelMetadata.getName());
    metadata.setLabel(rpcModelMetadata.getTitle());
    metadata.setRawDetails(rpcModelMetadata);

    return metadata;
  }
}
