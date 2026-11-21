package com.ouroboros.rpc.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class JarRPCMetadataProviderTest {

  @Test
  void rpcModelMetadataResourcesLoadFromTypedMetadataResources() {
    var metadataList = new JarRPCModelMetadataProvider().getAllRpcModelMetadata();

    var openApiModels = metadataList.stream()
        .filter(metadata -> "openApi".equals(metadata.getName()))
        .map(RPCModelMetadata::getTitle)
        .toList();

    assertEquals(1, openApiModels.size());
    assertEquals("Metadata OpenAPI", openApiModels.get(0));
  }

  @Test
  void rpcAuthCredentialMetadataResourcesLoadFromTypedMetadataResources() {
    var metadataList = new JarRPCAuthCredentialMetadataProvider().getAllRpcAuthCredentialMetadata();

    var credentials = metadataList.stream()
        .filter(metadata -> "sharedCredential".equals(metadata.getName()))
        .map(RPCCredentialMetadata::getTitle)
        .toList();

    assertEquals(1, credentials.size());
    assertEquals("Metadata Credential", credentials.get(0));
  }
}
