package com.ouroboros.uimodel;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

@SuppressWarnings("unused")
@Priority(10)
public class JarUIModelProvider implements UIModelProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarUIModelProvider.class);
  private static final String LEGACY_UI_MODEL_DIRECTORY = "META-INF/ouroboros/ui-model/";
  private static final String UI_MODEL_SUFFIX = ".ui-model.json";
  private static final Pattern JSON_FILE_PATTERN = Pattern.compile(".*\\.json");
  private Map<String, UIModel> uiModelMap;

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
        var loaded = List.copyOf(source.findResources(metadataSpec(Map.of())));
        var result = new TypedMetadataCatalog(ignored -> loaded).load(metadataSpec(Map.of()));
        if (result.diagnostics().isEmpty()) {
          snapshot = loaded;
        }
        return loaded;
      }
    };
  }

  public JarUIModelProvider() {
    this(CLASSPATH_RESOURCES);
  }

  JarUIModelProvider(MetadataSource source) {
    this(cacheResources(source));
  }

  private JarUIModelProvider(Supplier<List<MetadataResource>> resources) {
    this.resources = resources;
    loadDefinitions();
  }

  private static Try<SimpleUIModel> parseUIModel(MetadataResource resource, Map<String, MicroWebModel> microWebMap) {
    return Json.tryToBean(SimpleUIModel.class, resource.content())
        .map(uiModel -> normalizeUIModel(resource.resourceName(), uiModel, microWebMap));
  }

  private static SimpleUIModel normalizeUIModel(String resourceName, SimpleUIModel uiModel,
      Map<String, MicroWebModel> microWebMap) {
    uiModel.getExtraProp("microWebName")
        .map(Object::toString)
        .map(String::trim)
        .map(microWebMap::get)
        .map(micro -> Optional.ofNullable(micro.getEntry()).orElse(micro.getPublicPath()))
        .ifPresent(entry -> uiModel.setExtraProp("entry", entry));

    if (StringUtils.isBlank(uiModel.getPath())) {
      uiModel.setPath(toUIPath(stripJsonSuffix(resourceName)));
    }
    return uiModel;
  }

  private static String toUIPath(String path) {
    return path.startsWith("/") ? path : "/" + path;
  }

  private static String stripJsonSuffix(String resourceName) {
    if (resourceName.endsWith(UI_MODEL_SUFFIX)) {
      return resourceName.substring(0, resourceName.length() - UI_MODEL_SUFFIX.length());
    }
    return resourceName.substring(0, resourceName.length() - 5);
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("UI 模型元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("UI 模型元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  private synchronized Map<String, UIModel> loadDefinitions() {
    if (uiModelMap != null) {
      return uiModelMap;
    }
    try {
      var microWebMap = SPI
          .getCachedReversedServiceStream(MicroWebModelProvider.class)
          .map(MicroWebModelProvider::getAllMicroWebModel)
          .flatMap(Collection::stream)
          .collect(
              () -> new TreeMap<String, MicroWebModel>(String::compareToIgnoreCase),
              (map, model) -> {
                var name = model.getName();
                if (StringUtils.isBlank(name)) {
                  return;
                }
                map.put(name, model);
              },
              Map::putAll
          );

      var spec = metadataSpec(microWebMap);
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarUIModelProvider::logDiagnostic);
      Map<String, UIModel> models = result.values().values().stream()
          .collect(() -> new TreeMap<String, UIModel>(String::compareToIgnoreCase),
              (map, model) -> map.put(model.getPath(), model), Map::putAll);
      if (result.diagnostics().isEmpty()) {
        uiModelMap = models;
      }
      return models;
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarUIModelProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<SimpleUIModel> metadataSpec(Map<String, MicroWebModel> microWebMap) {
    return TypedMetadataSpec.<SimpleUIModel>builder(SimpleUIModel.class)
        .typedSuffix(UI_MODEL_SUFFIX)
        .sourcePlan(TypedMetadataSpec.SourcePlan.LEGACY_DIRECTORY_THEN_TYPED_SUFFIX)
        .legacyDirectory(LEGACY_UI_MODEL_DIRECTORY)
        .legacyFilePattern(JSON_FILE_PATTERN)
        .resourceParser(resource -> parseUIModel(resource, microWebMap))
        .keyExtractor(SimpleUIModel::getPath)
        .build();
  }

  @Override
  public Optional<UIModel> getUIModel(String path) {
    return Optional.ofNullable(loadDefinitions()).map(map -> map.get(path));
  }

  @Override
  public Map<String, UIModel> getUIModelMap() {
    return Optional.ofNullable(loadDefinitions()).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }
}
