package com.ouroboros.security.rbac.provider;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.DefaultAuthority;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Component
public class JarAuthorityProvider implements AuthorityProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarAuthorityProvider.class);

  private static final String LEGACY_AUTHORITY_DIRECTORY = "META-INF/ouroboros/authority/";
  private static final String AUTHORITY_SUFFIX = ".authority.json";
  private static final Pattern JSON_FILE_PATTERN = Pattern.compile(".*\\.json");

  private Map<String, Authority> authorities;

  private final Supplier<List<MetadataResource>> resources;

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());

  public JarAuthorityProvider() {
    this(CLASSPATH_RESOURCES);
  }

  JarAuthorityProvider(MetadataSource source) {
    this(cacheResources(source));
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

  private JarAuthorityProvider(Supplier<List<MetadataResource>> resources) {
    this.resources = resources;
    loadDefinitions();
  }

  private synchronized Map<String, Authority> loadDefinitions() {
    if (authorities != null) {
      return authorities;
    }
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarAuthorityProvider::logDiagnostic);
      Map<String, Authority> loadedAuthorities = result.values().values().stream()
          .collect(
              () -> new TreeMap<>(String::compareToIgnoreCase),
              (map, authority) -> map.put(authority.getFullName(), authority),
              Map::putAll
          );
      if (result.diagnostics().isEmpty()) {
        authorities = loadedAuthorities;
      }
      return loadedAuthorities;
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarAuthorityProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<Authority> metadataSpec() {
    return TypedMetadataSpec.<Authority>builder(Authority.class)
        .typedSuffix(AUTHORITY_SUFFIX)
        .sourcePlan(TypedMetadataSpec.SourcePlan.LEGACY_DIRECTORY_THEN_TYPED_SUFFIX)
        .legacyDirectory(LEGACY_AUTHORITY_DIRECTORY)
        .legacyFilePattern(JSON_FILE_PATTERN)
        .resourceParser(resource -> Json.tryToBean(DefaultAuthority.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(Authority::getFullName)
        .build();
  }

  @Override
  public List<Authority> getAuthority(String fullName) {
    return Optional.ofNullable(loadDefinitions().get(fullName)).stream().collect(Collectors.toList());
  }

  @Override
  public List<Authority> getAllAuthorities() {
    return loadDefinitions().values().stream().collect(Collectors.toList());
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("权限元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("权限元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
