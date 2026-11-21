package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataSourceType;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.util.Json;

/**
 * @author liansz
 **/
@Service
public class DictMetadataCollector {

  private final DataDictService service;
  public static final String METADATA_TYPE = "data-dict";

  public DictMetadataCollector(DataDictService service) {
    this.service = service;
    Proxy.collector = this;
  }

  public List<Metadata> getAllMetadata() {
    return service.searchAll().stream().map(origin -> {
      var metadata = new Metadata();
      metadata.setName(origin.getName());
      metadata.setType(METADATA_TYPE);
      metadata.setLabel(origin.getTitle());
      metadata.setPriority(Integer.MAX_VALUE);
      metadata.setSourceClassName("");
      metadata.setSourceType(MetadataSourceType.LOW_CODE);
      metadata.setDetails(Json.toMap(Json.toJsonString(origin)));

      return metadata;
    }).collect(Collectors.toList());
  }

  public static class Proxy implements MetadataCollector {
    private static DictMetadataCollector collector;

    @Override
    public String getMetadataType() {
      return METADATA_TYPE;
    }

    @Override
    public List<Metadata> getAllMetadata() {
      return collector.getAllMetadata();
    }
  }
}
