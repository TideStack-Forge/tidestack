package com.ouroboros.webapi;

import java.util.Collection;

public interface RequestMappingMetadataProvider {
  Collection<RequestMappingMetadata> getRequestMappingMetadata();
}
