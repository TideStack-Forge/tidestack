package com.ouroboros.configuration.provider;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadataProvider;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.util.Json;

@Priority(0)
public class JarConfigurationGroupMetadataProvider implements ConfigurationGroupMetadataProvider {

  private static final Logger logger = LoggerFactory.getLogger(JarConfigurationGroupMetadataProvider.class);

  private static final String METADATA_PACKAGE_PATH = "META-INF/ouroboros/configuration-groups.json";

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(
      () -> new ClasspathMetadataFiles().legacyThenMetadataResourcesForOverride(METADATA_PACKAGE_PATH));
  private final Supplier<List<MetadataResource>> resources;

  public JarConfigurationGroupMetadataProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarConfigurationGroupMetadataProvider(Supplier<List<MetadataResource>> loader) {
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
  public List<ConfigurationGroupMetadata> getAllConfigurationGroupMetadata() {
    try {
      return resources.get().stream()
          .filter(resource -> ObjectUtils.isNotEmpty(resource.content()))
          .map(resource -> parseGroupMetadataResource(resource.resourceName(), resource.content()))
          .flatMap(List::stream)
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarConfigurationGroupMetadataProvider", e);
      return List.of();
    }
  }

  static List<ConfigurationGroupMetadata> parseGroupMetadataResource(String resourcePath, String text) {
    return tryParseResource(text)
        .onFailure(e -> logger.warn("解析配置分组元数据资源 {} 失败，已跳过", resourcePath, e))
        .getOrElse(List.of());
  }

  private static Try<List<ConfigurationGroupMetadata>> tryParseResource(String text) {
    return Json.tryToList(new TypeReference<List<ConfigurationGroupMetadata>>() {
        }, text)
        .map(List::copyOf);
  }

}
