package com.ouroboros.devhelper.metadata;

import java.util.List;

import com.ouroboros.dev.metadata.Metadata;

/**
 * @author liansz
 **/
public interface MetadataCollector {
  String getMetadataType();

  List<Metadata> getAllMetadata();
}
