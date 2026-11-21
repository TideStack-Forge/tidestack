package com.ouroboros.appmodule;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.appmodule.model.JarAppModule;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.util.Json;

public class JarAppModuleProvider implements AppModuleProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarAppModuleProvider.class);
  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(
      () -> new ClasspathMetadataFiles().legacyThenMetadataResourcesForOverride("META-INF/ouroboros/app-modules.json"));
  private final Supplier<List<MetadataResource>> resources;

  public JarAppModuleProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarAppModuleProvider(Supplier<List<MetadataResource>> loader) {
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
        if (loaded.stream().allMatch(resource -> tryParseResource(resource.content()).isSuccess())) {
          snapshot = loaded;
        }
        return loaded;
      }
    };
  }

  @Override
  public List<AppModule> getAppModules() {
    try {
      return resources.get().stream()
          .map(JarAppModuleProvider::parseResource)
          .flatMap(List::stream)
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarAppModuleProvider", e);
      return List.of();
    }
  }

  private static List<JarAppModule> parseResource(MetadataResource resource) {
    return tryParseResource(resource.content())
        .onFailure(e -> logger.warn("解析模块资源 {} 失败，已跳过", resource.resourceName(), e))
        .getOrElse(List.of());
  }

  private static Try<List<JarAppModule>> tryParseResource(String text) {
    return Json.tryToList(new TypeReference<List<JarAppModule>>() {
        }, text)
        .map(List::copyOf);
  }

}
