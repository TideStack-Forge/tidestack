package com.ouroboros.data.dict.provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.util.Json;

@Order(JarDataDictMetadataProvider.ORDER)
public class JarDataDictMetadataProvider implements DataDictMetadataProvider {
  static final int ORDER = 0;
  private static final Logger logger = LoggerFactory.getLogger(JarDataDictMetadataProvider.class);
  private static final String FILE_EXTENSION = ".dict.json";

  // 仅缓存不可变文本，避免共享调用方可修改的模型。
  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());
  private final Supplier<List<MetadataResource>> resources;

  public JarDataDictMetadataProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarDataDictMetadataProvider(MetadataSource source) {
    resources = cacheResources(source);
  }

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

  @Override
  public Map<String, DataDict> getDataDicts() {
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarDataDictMetadataProvider::logDiagnostic);
      return Collections.unmodifiableMap(result.values());
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarDataDictMetadataProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<DataDict> metadataSpec() {
    return TypedMetadataSpec.<DataDict>builder(DataDict.class)
        .typedSuffix(FILE_EXTENSION)
        .resourceParser(resource -> Json.tryToBean(DataDict.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(DataDict::getName)
        .build();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("加载字典模型资源({})失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("加载字典模型资源({})跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
