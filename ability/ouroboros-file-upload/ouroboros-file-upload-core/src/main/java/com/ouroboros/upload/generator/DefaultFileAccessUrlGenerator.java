package com.ouroboros.upload.generator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Map;

/**
 * 默认的基于前缀的文件访问URL生成器
 */
public class DefaultFileAccessUrlGenerator implements FileAccessUrlGenerator {
  @Override
  public String generateUrl(String storageFullPath, Map<String, String> options) {
    var prefix = options.getOrDefault("fileUrlPrefix", "/");
    com.ouroboros.storage.ObjectKeys.validate(storageFullPath);
    String encoded = Arrays.stream(storageFullPath.split("/"))
        .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
        .collect(Collectors.joining("/"));
    return prefix.replaceAll("/+$", "") + "/" + encoded;
  }

  public static class Builder implements FileAccessUrlGeneratorBuilder {
    @Override
    public Boolean support(String strategyName) {
      if ("default".equalsIgnoreCase(strategyName)) {
        return true;
      }
      if ("prefix".equalsIgnoreCase(strategyName)) {
        return true;
      }
      return false;
    }

    @Override
    public FileAccessUrlGenerator build() {
      return new DefaultFileAccessUrlGenerator();
    }
  }
}
