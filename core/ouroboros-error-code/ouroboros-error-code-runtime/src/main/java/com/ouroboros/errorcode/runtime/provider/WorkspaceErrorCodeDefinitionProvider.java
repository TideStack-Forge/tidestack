package com.ouroboros.errorcode.runtime.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.AppContext;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.provider.ErrorCodeDefinitionProvider;
import com.ouroboros.util.Json;

public class WorkspaceErrorCodeDefinitionProvider implements ErrorCodeDefinitionProvider {
  private static final String METADATA_FILE_NAME = "error-codes.error.json";

  private final AppContext appContext;

  public WorkspaceErrorCodeDefinitionProvider() {
    this(null);
  }

  public WorkspaceErrorCodeDefinitionProvider(AppContext appContext) {
    this.appContext = appContext;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public List<ErrorCodeDefinition> getDefinitions() {
    var homePath = resolveHomePath();
    if (homePath == null || homePath.trim().isEmpty()) {
      return Collections.emptyList();
    }

    var metadataFile = Paths.get(homePath, METADATA_FILE_NAME);
    if (!Files.exists(metadataFile) || !Files.isRegularFile(metadataFile)) {
      return Collections.emptyList();
    }

    return readDefinitions(metadataFile);
  }

  private String resolveHomePath() {
    if (appContext != null) {
      return appContext.getHomePath();
    }

    ApplicationContext applicationContext = SpringContextUtil.getApplicationContext();
    if (applicationContext == null) {
      return null;
    }

    try {
      return applicationContext.getBean(AppContext.class).getHomePath();
    } catch (Exception contextLookupFailure) {
      return null;
    }
  }

  private static List<ErrorCodeDefinition> readDefinitions(Path path) {
    try {
      var content = Files.readString(path);
      return Json.tryToBean(new TypeReference<List<ErrorCodeDefinition>>() {
      }, content).getOrElseThrow(cause -> new IllegalStateException("错误码集合文件格式错误: " + path, cause));
    } catch (Exception readFailure) {
      throw new IllegalStateException("读取错误码集合文件失败: " + path, readFailure);
    }
  }
}
