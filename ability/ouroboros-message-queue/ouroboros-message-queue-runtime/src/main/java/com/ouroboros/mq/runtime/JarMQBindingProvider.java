package com.ouroboros.mq.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.binding.MQBindingProvider;
import com.ouroboros.util.Json;

@Priority(0)
public class JarMQBindingProvider implements MQBindingProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarMQBindingProvider.class);
  private static final String MQ_BINDING_SUFFIX = ".mq.json";

  // 仅缓存不可变文本，避免共享调用方可修改的模型。
  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());
  private final Supplier<List<MetadataResource>> resources;

  public JarMQBindingProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarMQBindingProvider(MetadataSource source) {
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
  public List<MQBindingProperties<?>> getAllBindings() {
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarMQBindingProvider::logDiagnostic);
      return result.values()
          .values()
          .stream()
          .map(binding -> (MQBindingProperties<?>) binding)
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarMQBindingProvider", e);
      return List.of();
    }
  }

  private static TypedMetadataSpec<MQBindingProperties> metadataSpec() {
    return TypedMetadataSpec.builder(MQBindingProperties.class)
        .typedSuffix(MQ_BINDING_SUFFIX)
        .resourceParser(resource -> Json.tryToBean(MQBindingProperties.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(MQBindingProperties::getBindingName)
        .build();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("消息队列绑定元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("消息队列绑定元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
