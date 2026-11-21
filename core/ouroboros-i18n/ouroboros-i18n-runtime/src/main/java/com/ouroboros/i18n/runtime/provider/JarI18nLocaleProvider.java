package com.ouroboros.i18n.runtime.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

@Priority(0)
public class JarI18nLocaleProvider implements I18nLocaleProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarI18nLocaleProvider.class);
  private static final String I18N_SUFFIX = ".i18n.json";

  private Map<String, I18nLocale> localeMap;

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

  public JarI18nLocaleProvider() {
    this(CLASSPATH_RESOURCES);
  }

  JarI18nLocaleProvider(MetadataSource source) {
    this(cacheResources(source));
  }

  private JarI18nLocaleProvider(Supplier<List<MetadataResource>> resources) {
    this.resources = resources;
    loadDefinitions();
  }

  private synchronized Map<String, I18nLocale> loadDefinitions() {
    if (localeMap != null) {
      return localeMap;
    }
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarI18nLocaleProvider::logDiagnostic);
      Map<String, I18nLocale> locales = result.values();
      if (result.diagnostics().isEmpty()) {
        localeMap = locales;
      }
      return locales;
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarI18nLocaleProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<I18nLocale> metadataSpec() {
    return TypedMetadataSpec.<I18nLocale>builder(I18nLocale.class)
        .typedSuffix(I18N_SUFFIX)
        .resourceParser(resource -> com.ouroboros.util.Json.tryToBean(I18nLocaleImpl.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(I18nLocale::getLocale)
        .build();
  }

  @Override
  public Optional<I18nLocale> getDictionary(String locale) {
    return Optional.ofNullable(loadDefinitions().get(locale));
  }

  @Override
  public List<String> getSupportedLocales() {
    return Collections.unmodifiableList(new ArrayList<>(loadDefinitions().keySet()));
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("国际化元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("国际化元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
