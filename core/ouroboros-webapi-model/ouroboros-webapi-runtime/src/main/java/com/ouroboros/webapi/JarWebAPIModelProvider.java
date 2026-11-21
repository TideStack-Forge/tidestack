package com.ouroboros.webapi;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

@Priority(0)
public class JarWebAPIModelProvider implements WebAPIModelProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarWebAPIModelProvider.class);
  private static final String WEBAPI_SUFFIX = ".webapi.json";

  private Map<String, WebAPIModel> apiModelMap;

  private final Supplier<List<MetadataResource>> resources;

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());

  private static Supplier<List<MetadataResource>> cacheResources(MetadataSource source) {
    return new Supplier<>() {
      private List<MetadataResource> snapshot;

      @Override
      public synchronized List<MetadataResource> get() {
        if (snapshot != null) {
          return snapshot;
        }
        var loaded = List.copyOf(source.findResources(metadataSpec()));
        var result = new TypedMetadataCatalog(ignored -> loaded).load(metadataSpec());
        if (result.diagnostics().isEmpty()) {
          snapshot = loaded;
        }
        return loaded;
      }
    };
  }

  public JarWebAPIModelProvider() {
    this(CLASSPATH_RESOURCES);
  }

  JarWebAPIModelProvider(MetadataSource source) {
    this(cacheResources(source));
  }

  private JarWebAPIModelProvider(Supplier<List<MetadataResource>> resources) {
    this.resources = resources;
    loadDefinitions();
  }

  private synchronized Map<String, WebAPIModel> loadDefinitions() {
    if (apiModelMap != null) {
      return apiModelMap;
    }
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarWebAPIModelProvider::logDiagnostic);
      Map<String, WebAPIModel> models = result.values();
      if (result.diagnostics().isEmpty()) {
        apiModelMap = models;
      }
      return models;
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarWebAPIModelProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<WebAPIModel> metadataSpec() {
    return TypedMetadataSpec.<WebAPIModel>builder(WebAPIModel.class)
        .typedSuffix(WEBAPI_SUFFIX)
        .resourceParser(resource -> com.ouroboros.util.Json.tryToBean(SimpleWebAPIModel.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(JarWebAPIModelProvider::getModelKey)
        .build();
  }

  @Override
  public Map<String, WebAPIModel> getWebAPIModelMap() {
    return Collections.unmodifiableMap(loadDefinitions());
  }

  private static String getModelKey(WebAPIModel model) {
    if (StringUtils.isBlank(model.getMethod()) || StringUtils.isBlank(model.getPath())) {
      return "";
    }
    return model.getApi();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("WebAPI 元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("WebAPI 元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
