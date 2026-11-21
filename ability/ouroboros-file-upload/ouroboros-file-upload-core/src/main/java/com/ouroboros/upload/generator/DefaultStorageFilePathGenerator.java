package com.ouroboros.upload.generator;

import static com.ouroboros.upload.generator.TemplateUtils.processDateTimeParts;
import static com.ouroboros.upload.generator.TemplateUtils.processRandomPart;

import java.nio.file.Paths;
import java.util.Map;

/**
 * 默认的基于模版的文件存储路径生成器
 */
public class DefaultStorageFilePathGenerator implements StorageFilePathGenerator {
  @Override
  public String generatorPath(String fileName, Map<String, String> options) {
    var filePathPrefix = options.getOrDefault("filePathPrefix", "");
    var filePathTemplate = options.getOrDefault("filePathTemplate", "");

    if (filePathTemplate.isEmpty()) {
      return "";
    }

    var path = processDateTimeParts(filePathTemplate);
    path = processRandomPart(path);
    path = Paths.get(filePathPrefix, path).toString();
    return path.startsWith("/") ? path.substring(1) : path;
  }

  public static class Builder implements StorageFilePathGeneratorBuilder {
    @Override
    public Boolean support(String strategyName) {
      if ("default".equalsIgnoreCase(strategyName)) {
        return true;
      }
      if ("template".equalsIgnoreCase(strategyName)) {
        return true;
      }
      return false;
    }

    @Override
    public StorageFilePathGenerator build() {
      return new DefaultStorageFilePathGenerator();
    }
  }
}
