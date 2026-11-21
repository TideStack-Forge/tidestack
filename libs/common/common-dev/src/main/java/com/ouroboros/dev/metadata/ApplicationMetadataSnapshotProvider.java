package com.ouroboros.dev.metadata;

import java.util.List;

public interface ApplicationMetadataSnapshotProvider extends MetadataProvider {
  ApplicationMetadataSnapshot getApplicationMetadataSnapshot(String appName);

  @Override
  default List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
    return getApplicationMetadataSnapshot(appName).getModule(moduleName);
  }
}
