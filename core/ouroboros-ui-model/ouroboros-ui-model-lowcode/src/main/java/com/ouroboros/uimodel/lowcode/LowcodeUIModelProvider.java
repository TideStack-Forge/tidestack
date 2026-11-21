package com.ouroboros.uimodel.lowcode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import jakarta.annotation.Priority;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

@Component
@SuppressWarnings("unused")
public class LowcodeUIModelProvider {
  static final Logger logger = LoggerFactory.getLogger(LowcodeUIModelProvider.class);
  static final String MODEL_PATH = "ui-model";
  static final String MODEL_FILE_SUFFIX = ".ui-model.json";
  static final String LEGACY_MODEL_FILE_SUFFIX = ".json";

  @Autowired
  private AppContext appContext;
  private Map<String, UIModel> uiModels;

  public LowcodeUIModelProvider() {
    SingletonHolder.lowCodeUiModelProvider = this;
  }

  @EventListener(OuroborosMetadataRefreshingEvent.class)
  public void onMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
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

    var homePath = appContext.getHomePath();
    var modelPath = Paths.get(homePath, MODEL_PATH);
    if (!modelPath.toFile().exists()) {
      return;
    }

    Map<String, UIModel> loadedModels = new TreeMap<>(String::compareToIgnoreCase);
    loadModels(modelPath, LEGACY_MODEL_FILE_SUFFIX, true, microWebMap, loadedModels);
    loadModels(modelPath, MODEL_FILE_SUFFIX, false, microWebMap, loadedModels);
    uiModels = loadedModels;
  }

  private void loadModels(Path modelPath, String suffix, boolean excludeTypedSuffix,
      Map<String, MicroWebModel> microWebMap, Map<String, UIModel> loadedModels) {
    try (var stream = Files.walk(modelPath)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(suffix))
          .filter(path -> !excludeTypedSuffix || !path.getFileName().toString().endsWith(MODEL_FILE_SUFFIX))
          .map(path -> readModel(modelPath, path, suffix))
          .filter(Objects::nonNull)
          .peek(model -> applyMicroWebEntry(model, microWebMap))
          .forEach(model -> loadedModels.put(normalizePath(model.getPath()), model));
    } catch (IOException e) {
      logger.error("遍历低代码UI模型失败", e);
    }
  }

  private SimpleUIModel readModel(Path rootPath, Path path, String suffix) {
    try {
      var json = FileUtils.readFileToString(path.toFile(), "UTF-8");
      var result = Json.tryToBean(SimpleUIModel.class, json);
      if (result.isFailure()) {
        logger.error("载入UI模型失败: {}", rootPath.relativize(path), result.getCause());
        return null;
      }
      var model = result.get();
      if (StringUtils.isBlank(model.getPath())) {
        model.setPath(buildPathFromFileName(rootPath, path, suffix));
      }
      return model;
    } catch (IOException e) {
      logger.error("读取UI模型失败: {}", rootPath.relativize(path), e);
      return null;
    }
  }

  private String buildPathFromFileName(Path rootPath, Path path, String suffix) {
    var relativePath = rootPath.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    return stripSuffix(relativePath, suffix);
  }

  private String stripSuffix(String path, String suffix) {
    return path.endsWith(suffix) ? path.substring(0, path.length() - suffix.length()) : path;
  }

  private void applyMicroWebEntry(SimpleUIModel model, Map<String, MicroWebModel> microWebMap) {
    model.getExtraProp("microWebName")
        .map(Object::toString)
        .map(String::trim)
        .map(microWebMap::get)
        .map(micro -> Optional.ofNullable(micro.getEntry()).orElse(micro.getPublicPath()))
        .ifPresent(entry -> model.setExtraProp("entry", entry));
  }

  private String normalizePath(String path) {
    if (StringUtils.isBlank(path)) {
      return path;
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  public Optional<UIModel> getUIModel(String path) {
    return Optional.ofNullable(uiModels).map(map -> map.get(path));
  }

  public Map<String, UIModel> getUIModelMap() {
    return Optional.ofNullable(uiModels).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  @Priority(100)
  @LowCodeProvider
  public static class SingletonHolder implements UIModelProvider {
    static LowcodeUIModelProvider lowCodeUiModelProvider;

    @Override
    public Optional<UIModel> getUIModel(String path) {
      return Optional.ofNullable(lowCodeUiModelProvider).flatMap(provider -> provider.getUIModel(path));
    }

    @Override
    public Map<String, UIModel> getUIModelMap() {
      return Optional.ofNullable(lowCodeUiModelProvider).map(LowcodeUIModelProvider::getUIModelMap).orElse(Collections.emptyMap());
    }
  }
}
