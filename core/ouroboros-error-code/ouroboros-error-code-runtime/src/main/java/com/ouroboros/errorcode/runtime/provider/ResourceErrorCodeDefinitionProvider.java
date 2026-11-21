package com.ouroboros.errorcode.runtime.provider;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.provider.ErrorCodeDefinitionProvider;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.util.Json;

public class ResourceErrorCodeDefinitionProvider implements ErrorCodeDefinitionProvider {
  private static final String METADATA_FILE_NAME = "error-codes.error.json";

  @Override
  public int getOrder() {
    return 100;
  }

  @Override
  public List<ErrorCodeDefinition> getDefinitions() {
    return new ClasspathMetadataFiles().metadataContentStreamForOverride(METADATA_FILE_NAME)
        .flatMap(content -> readDefinitions(content).stream())
        .collect(Collectors.toList());
  }

  private List<ErrorCodeDefinition> readDefinitions(String content) {
    return Json.tryToBean(new TypeReference<List<ErrorCodeDefinition>>() {
    }, content).getOrElseThrow(cause -> new IllegalStateException("错误码资源文件格式错误: " + METADATA_FILE_NAME, cause));
  }
}
