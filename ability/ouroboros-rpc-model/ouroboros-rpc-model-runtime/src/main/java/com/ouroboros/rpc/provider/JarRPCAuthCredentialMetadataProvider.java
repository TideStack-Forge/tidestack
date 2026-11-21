package com.ouroboros.rpc.provider;

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
import com.ouroboros.rpc.security.metadata.RPCAuthCredentialMetadataProvider;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.Json;

/**
 * @author liansz
 **/
@Priority(0)
public class JarRPCAuthCredentialMetadataProvider implements RPCAuthCredentialMetadataProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarRPCAuthCredentialMetadataProvider.class);

  private static final String METADATA_FILE_SUFFIX = ".rpc.auth.json";

  // 仅缓存不可变文本，避免共享调用方可修改的模型。
  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());
  private final Supplier<List<MetadataResource>> resources;

  public JarRPCAuthCredentialMetadataProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarRPCAuthCredentialMetadataProvider(MetadataSource source) {
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
  public List<RPCCredentialMetadata> getAllRpcAuthCredentialMetadata() {
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarRPCAuthCredentialMetadataProvider::logDiagnostic);
      return result.values()
          .values()
          .stream()
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarRPCAuthCredentialMetadataProvider", e);
      return List.of();
    }
  }

  private static TypedMetadataSpec<RPCCredentialMetadata> metadataSpec() {
    return TypedMetadataSpec.builder(RPCCredentialMetadata.class)
        .typedSuffix(METADATA_FILE_SUFFIX)
        .resourceParser(resource -> Json.tryToBean(RPCCredentialMetadata.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(RPCCredentialMetadata::getName)
        .build();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("RPC 认证元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("RPC 认证元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
