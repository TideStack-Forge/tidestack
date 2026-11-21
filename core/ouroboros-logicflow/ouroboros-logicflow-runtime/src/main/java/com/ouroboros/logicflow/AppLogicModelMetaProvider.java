package com.ouroboros.logicflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelMetaProvider;
import com.ouroboros.util.Json;

@Component
@ConditionalOnBean(AppContext.class)
public class AppLogicModelMetaProvider implements FlowModelMetaProvider {
  private static final Logger logger = LoggerFactory.getLogger(AppLogicModelMetaProvider.class);
  private static final String LOGIC_FLOW_MODEL_PATH = "logic-flow";
  private static final String MODEL_FILE_SUFFIX = ".logicflow.json";
  private static final String LEGACY_MODEL_FILE_SUFFIX = ".json";
  AppContext appContext;
  volatile Map<String, FlowModelMeta> modelMetas;
  private volatile List<String> sourceDiagnostics = List.of();

  @Autowired
  public AppLogicModelMetaProvider(AppContext appContext) {
    this(appContext, true);
  }

  AppLogicModelMetaProvider(AppContext appContext, boolean refreshOnInit) {
    this.appContext = appContext;
    SingletonHolder.instance = this;
    if (refreshOnInit) {
      refresh();
    } else {
      modelMetas = Collections.emptyMap();
    }
  }

  void refresh() {
    sourceDiagnostics = List.of();
    final var metadataHome = Paths.get(appContext.getHomePath(), LOGIC_FLOW_MODEL_PATH);
    if (!Files.isDirectory(metadataHome)) {
      modelMetas = Collections.emptyMap();
      return;
    }

    try (var stream = walkModelFiles(metadataHome)) {
      var modelFiles = stream
          .filter(Files::isRegularFile)
          .collect(Collectors.toList());
      var loadedModels = new java.util.ArrayList<Try<FlowModelMeta>>();
      loadedModels.addAll(loadModelMetas(modelFiles, LEGACY_MODEL_FILE_SUFFIX, true));
      loadedModels.addAll(loadModelMetas(modelFiles, MODEL_FILE_SUFFIX, false));
      var diagnostics = new java.util.ArrayList<String>();
      var models = new TreeMap<String, FlowModelMeta>(String::compareToIgnoreCase);
      for (var loaded : loadedModels) {
        if (loaded.isFailure()) {
          diagnostics.add(loaded.getCause().getClass().getSimpleName() + ": " + loaded.getCause().getMessage());
        } else {
          var model = loaded.get();
          if (models.put(model.getName(), model) != null) {
            diagnostics.add("来源存在同名逻辑流: " + model.getName());
          }
        }
      }
      modelMetas = models;
      sourceDiagnostics = List.copyOf(diagnostics);
    } catch (Exception e) {
      logger.warn("扫描逻辑流元数据目录 {} 失败: {}", metadataHome, e.getMessage(), e);
      sourceDiagnostics = List.of("扫描逻辑流目录失败: " + e.getClass().getSimpleName());
      modelMetas = Collections.emptyMap();
    }
  }

  private List<Try<FlowModelMeta>> loadModelMetas(List<Path> modelFiles, String suffix, boolean excludeTypedSuffix) {
    return modelFiles.stream()
        .filter(path -> path.getFileName().toString().endsWith(suffix))
        .filter(path -> !excludeTypedSuffix || !path.getFileName().toString().endsWith(MODEL_FILE_SUFFIX))
        .map(this::loadModelMeta)
        .collect(Collectors.toList());
  }

  protected Stream<Path> walkModelFiles(Path metadataHome) throws IOException {
    return Files.walk(metadataHome);
  }

  protected String readFileContent(Path filePath) throws IOException {
    return String.join("", Files.readAllLines(filePath));
  }

  private Try<FlowModelMeta> loadModelMeta(Path filePath) {
    var fileContent = Try.of(() -> readFileContent(filePath))
        .onFailure(e -> logger.warn("跳过逻辑流元数据文件 {}，读取失败: {}", filePath, e.getMessage(), e));
    if (fileContent.isFailure()) {
      return Try.failure(fileContent.getCause());
    }

    var content = fileContent.get().trim();
    if (content.isEmpty()) {
      var failure = new RuntimeException(filePath.getFileName() + " is empty");
      logger.warn("跳过逻辑流元数据文件 {}，内容为空: {}", filePath, failure.getMessage(), failure);
      return Try.failure(failure);
    }

    var parsedModel = Json.tryToBean(FlowModelMeta.class, content)
        .onFailure(e -> logger.warn("跳过逻辑流元数据文件 {}，JSON 解析失败: {}", filePath, e.getMessage(), e));
    if (parsedModel.isFailure()) {
      return Try.failure(parsedModel.getCause());
    }

    var modelMeta = parsedModel.get();
    var flowName = modelMeta.getName();
    if (flowName == null || flowName.trim().isEmpty()) {
      var failure = new RuntimeException(filePath.getFileName() + " is missing flow name");
      logger.warn("跳过逻辑流元数据文件 {}，缺少逻辑流名称: {}", filePath, failure.getMessage(), failure);
      return Try.failure(failure);
    }
    return Try.success(modelMeta);
  }

  @EventListener
  void onOuroborosMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
    refresh();
  }

  @Override
  public Optional<FlowModelMeta> getFlowModelMeta(String fullName) {
    return Optional.ofNullable(modelMetas.get(fullName));
  }

  @Override
  public Map<String, FlowModelMeta> getFlowModelMetas() {
    return modelMetas;
  }

  @Override
  public com.ouroboros.logicflow.model.FlowModelSource observeSource() {
    return com.ouroboros.logicflow.model.FlowModelSource.capture("app", 100, false,
        sourceDiagnostics.isEmpty(), sourceDiagnostics, modelMetas);
  }

  @Priority(100)
  @LowCodeProvider
  public static class SingletonHolder implements FlowModelMetaProvider {
    private static AppLogicModelMetaProvider instance;

    @Override
    public Optional<FlowModelMeta> getFlowModelMeta(String fullName) {
      return Optional.ofNullable(instance).flatMap(provider -> provider.getFlowModelMeta(fullName));
    }

    @Override
    public Map<String, FlowModelMeta> getFlowModelMetas() {
      return Optional.ofNullable(instance)
          .map(AppLogicModelMetaProvider::getFlowModelMetas)
          .orElse(Collections.emptyMap());
    }
    @Override
    public com.ouroboros.logicflow.model.FlowModelSource observeSource() {
      return instance == null
          ? com.ouroboros.logicflow.model.FlowModelSource.capture("app", 100, false, true, List.of(), Map.of())
          : instance.observeSource();
    }

  }
}
