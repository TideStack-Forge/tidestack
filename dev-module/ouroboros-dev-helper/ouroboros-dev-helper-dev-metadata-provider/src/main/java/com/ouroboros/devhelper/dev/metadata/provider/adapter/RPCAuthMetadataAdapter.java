package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

@Component
@Order(-1)
public class RPCAuthMetadataAdapter extends AbstractDevelopServiceAdapter<RPCCredentialMetadata> {

  public RPCAuthMetadataAdapter(DevelopService<RPCCredentialMetadata> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof RPCCredentialMetadata rpcCredentialMetadata)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(rpcCredentialMetadata.getName());
    metadata.setLabel(rpcCredentialMetadata.getTitle());
    metadata.setRawDetails(rpcCredentialMetadata);

    return metadata;
  }
}
