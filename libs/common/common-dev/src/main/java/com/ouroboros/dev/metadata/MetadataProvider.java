package com.ouroboros.dev.metadata;

import java.util.List;

public interface MetadataProvider {
  List<Metadata> getAppAllMetadataByModule(String appName, String moduleName);
}
