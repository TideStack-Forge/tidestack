package com.ouroboros.uischema;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.util.Json;
import com.ouroboros.util.Strings;

/**
 * @author liansz
 */
@Component
public class AppUiSchemaSchemaProvider {
  private final static Logger logger = LoggerFactory.getLogger(AppUiSchemaSchemaProvider.class);
  private final String MODEL_PATH = "ui-schema";
  private final String MODEL_FILE_SUFFIX = ".ui-schema.json";
  private final String LEGACY_MODEL_FILE_SUFFIX = ".json";

  private final AppContext appContext;

  private Map<String, Object> modelMetas;

  @Autowired
  public AppUiSchemaSchemaProvider(AppContext appContext) {
    this.appContext = appContext;
    Proxy.provider = this;
  }

  @EventListener
  public void onMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
    refresh();
  }

  private Optional<Object> getSchema(String fullName) {
    return Optional.ofNullable(modelMetas).map(map -> map.get(fullName));
  }

  private Map<String, Object> getSchemaMap() {
    return Optional.ofNullable(modelMetas).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  private void refresh() {
    final var finalAppBusinessModelPath = Paths.get(appContext.getHomePath(), MODEL_PATH);
    if (!Try.of(() -> finalAppBusinessModelPath.toFile().exists()).getOrElse(false)) {
      logger.warn("低代码元数据目录中不存在 ui-schema 目录，跳过加载");
      modelMetas = Collections.emptyMap();
      return;
    }

    Map<String, Object> schemas = new TreeMap<>(String::compareToIgnoreCase);
    loadLegacySchemas(finalAppBusinessModelPath, schemas);
    loadMetadataSchemas(finalAppBusinessModelPath, schemas);
    modelMetas = schemas;
  }

  private void loadLegacySchemas(java.nio.file.Path rootPath, Map<String, Object> schemas) {
    try (var stream = Files.walk(rootPath)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(LEGACY_MODEL_FILE_SUFFIX))
          .filter(path -> !path.getFileName().toString().endsWith(MODEL_FILE_SUFFIX))
          .forEach(filePath -> Json.tryToBean(SchemaNodeWrapper.class, readFile(filePath))
              .peek(wrapper -> putWrappedSchema(filePath, wrapper, schemas))
              .onFailure(e -> logger.warn("低代码元数据中的 legacy ui-schema 文件格式错误: {}", filePath.getFileName(), e)));
    } catch (Exception e) {
      logger.error("从低代码元数据中加载 legacy UI Schema 失败", e);
    }
  }

  private void loadMetadataSchemas(java.nio.file.Path rootPath, Map<String, Object> schemas) {
    try (var stream = Files.walk(rootPath)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(MODEL_FILE_SUFFIX))
          .forEach(filePath -> Json.tryToBean(SchemaNodeWrapper.class, readFile(filePath))
              .peek(wrapper -> putWrappedSchema(filePath, wrapper, schemas))
              .onFailure(e -> logger.warn("低代码元数据中的 ui-schema 文件格式错误: {}", filePath.getFileName(), e)));
    } catch (Exception e) {
      logger.error("从低代码元数据中加载 UI Schema 失败", e);
    }
  }

  private void putWrappedSchema(java.nio.file.Path filePath, SchemaNodeWrapper wrapper, Map<String, Object> schemas) {
    if (ObjectUtils.anyNull(wrapper.getName(), wrapper.getSchema())) {
      logger.warn("低代码元数据中的 ui-schema 文件格式错误: {}", filePath.getFileName());
      return;
    }
    schemas.put(normalizeSchemaPath(wrapper.getName()), wrapper.getSchema());
  }

  private String readFile(java.nio.file.Path filePath) {
    return Try.of(() -> Files.readString(filePath)).getOrElse("");
  }

  private String buildKebabCasePath(String fullName) {
    final var path = Stream.of(fullName.split("\\."))
        .map(part -> Strings.toKebabCase(part))
        .collect(Collectors.joining("/"));
    return "/" + path;
  }

  private String normalizeSchemaPath(String name) {
    String trimmedName = name.trim();
    if (trimmedName.startsWith("/")) {
      return trimmedName;
    }
    if (trimmedName.contains("/")) {
      return "/" + trimmedName;
    }
    return buildKebabCasePath(trimmedName);
  }

  @Priority(100)
  @LowCodeProvider
  public static class Proxy implements UISchemaProvider {
    private static AppUiSchemaSchemaProvider provider;

    @Override
    public Optional<Object> getUISchema(String fullName) {
      return Optional.ofNullable(provider).flatMap(provider -> provider.getSchema(fullName));
    }

    @Override
    public Map<String, Object> getUISchemaMap() {
      return Optional.ofNullable(provider).map(AppUiSchemaSchemaProvider::getSchemaMap).orElse(Collections.emptyMap());
    }
  }
}
