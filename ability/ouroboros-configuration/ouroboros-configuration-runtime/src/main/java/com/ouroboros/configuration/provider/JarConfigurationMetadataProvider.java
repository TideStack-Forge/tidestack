package com.ouroboros.configuration.provider;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.util.Json;

/**
 * @author liansz
 **/
@Priority(0)
public class JarConfigurationMetadataProvider implements ConfigurationMetadataProvider {

  private static final Logger logger = LoggerFactory.getLogger(JarConfigurationMetadataProvider.class);

  /**
   * 配置项元数据文件路径
   */
  private static final String LEGACY_METADATA_PACKAGE_PATH = "META-INF/ouroboros/configuration.json";
  private static final String METADATA_PACKAGE_PATH = "META-INF/ouroboros/configurations.json";

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(() -> {
    var metadataFiles = new ClasspathMetadataFiles();
    return Stream.of(LEGACY_METADATA_PACKAGE_PATH, METADATA_PACKAGE_PATH)
        .flatMap(path -> metadataFiles.legacyThenMetadataResourcesForOverride(path).stream())
        .toList();
  });
  private final Supplier<List<MetadataResource>> resources;

  public JarConfigurationMetadataProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarConfigurationMetadataProvider(Supplier<List<MetadataResource>> loader) {
    resources = cacheResources(loader);
  }

  private static Supplier<List<MetadataResource>> cacheResources(Supplier<List<MetadataResource>> loader) {
    return new Supplier<>() {
      private List<MetadataResource> snapshot;

      @Override
      public synchronized List<MetadataResource> get() {
        if (snapshot != null) {
          return snapshot;
        }
        var loaded = List.copyOf(loader.get());
        if (loaded.stream()
            .filter(resource -> ObjectUtils.isNotEmpty(resource.content()))
            .allMatch(resource -> tryParseResource(resource.content()).isSuccess())) {
          snapshot = loaded;
        }
        return loaded;
      }
    };
  }

  @Override
  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    try {
      return resources.get().stream()
          .filter(resource -> ObjectUtils.isNotEmpty(resource.content()))
          .map(resource -> parseMetadataResource(resource.resourceName(), resource.content()))
          .flatMap(List::stream)
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarConfigurationMetadataProvider", e);
      return List.of();
    }
  }

  static List<ConfigurationMetadata> parseMetadataResource(String resourcePath, String text) {
    return tryParseResource(text)
        .onFailure(e -> logger.warn("解析配置项元数据资源 {} 失败，已跳过", resourcePath, e))
        .getOrElse(List.of());
  }

  private static Try<List<ConfigurationMetadata>> tryParseResource(String text) {
    return Json.tryToList(new TypeReference<List<ConfigurationMetadata>>() {
        }, text)
        .map(List::copyOf);
  }

}
