package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class AuthorityMetadataCollector implements MetadataCollector {

  private final List<AuthorityProvider> providers;
  private final static String METADATA_TYPE = "authority";

  public AuthorityMetadataCollector() {
    providers = SPI
        .getSortedServiceStream(AuthorityProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getAllAuthorities().stream().map(origin -> {
          var metadata = new Metadata();
          metadata.setName(origin.getFullName());
          metadata.setType(METADATA_TYPE);
          metadata.setLabel(origin.getTitle());
          metadata.setPriority(MetadataAnnotationUtil.getPriority(provider.getClass()));
          metadata.setSourceClassName(provider.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(provider.getClass()));
          metadata.setDetails(Json.toMap(Json.toJsonString(origin)));

          return metadata;
        })
    ).collect(Collectors.toList());
  }
}
