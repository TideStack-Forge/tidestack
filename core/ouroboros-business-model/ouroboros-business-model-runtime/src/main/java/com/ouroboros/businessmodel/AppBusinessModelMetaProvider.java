package com.ouroboros.businessmodel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;

@Component
@SuppressWarnings("unused")
public class AppBusinessModelMetaProvider implements BusinessModelMetaProvider {
  private final String BUSINESS_MODEL_PATH = "business-model";
  private final String MODULE_FILE_NAME = "modules.json";
  private final String MODEL_FILE_SUFFIX = ".business.json";
  AppContext appContext;
  Map<String, BusinessModelMeta> businessModelMetas;

  @Autowired
  public AppBusinessModelMetaProvider(AppContext appContext) {
    this.appContext = appContext;
    SingletonHolder.instance = this;
    refresh();
  }

  void refresh() {

    final var finalAppBusinessModelPath = Paths.get(appContext.getHomePath(), BUSINESS_MODEL_PATH);

    try (var stream = Files.walk(finalAppBusinessModelPath)) {
      final var modelFiles = stream
          .filter(Files::isRegularFile)
          .collect(Collectors.toList());
      final var modelList = new java.util.ArrayList<Try<BusinessModelMeta>>();
      modelList.addAll(loadModelFiles(modelFiles));
      // TODO: 抛出warning
      businessModelMetas = modelList.stream()
          .filter(Try::isSuccess)
          .map(Try::get)
          .collect(() -> new TreeMap<>(String::compareToIgnoreCase), (map, model) -> map.put(model.getFullName(), model), Map::putAll);
    } catch (Exception e) {
      // TODO: 抛出warning
      businessModelMetas = Collections.emptyMap();
    }
  }

  private List<Try<BusinessModelMeta>> loadModelFiles(List<Path> modelFiles) {
    return modelFiles.stream()
          .filter(path -> path.getFileName().toString().endsWith(MODEL_FILE_SUFFIX))
          .filter(path -> !MODULE_FILE_NAME.equals(path.getFileName().toString()))
          .map(filePath -> {
            var fileContent = Try.of(() -> String.join("", Files.readAllLines(filePath))).getOrElse("");
            if (fileContent.trim().isEmpty()) {
              return Try.<BusinessModelMeta>failure(new RuntimeException(filePath.getFileName() + " is empty"));
            }
            return BusinessModelMetaParser.parse(fileContent);
          })
          .collect(Collectors.toList());
  }

  @EventListener
  void onOuroborosMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
    refresh();
  }

  @Override
  public Optional<BusinessModelMeta> getBusinessModelMeta(String fullName) {
    return Optional.ofNullable(businessModelMetas.get(fullName));
  }

  @Override
  public Map<String, BusinessModelMeta> getBusinessModelMetaMap() {
    return businessModelMetas;
  }

  public static class SingletonHolder implements BusinessModelMetaProvider {
    private static AppBusinessModelMetaProvider instance;

    @Override
    public Optional<BusinessModelMeta> getBusinessModelMeta(String fullName) {
      return Optional.ofNullable(instance).flatMap(provider -> provider.getBusinessModelMeta(fullName));
    }

    @Override
    public Map<String, BusinessModelMeta> getBusinessModelMetaMap() {
      return Optional.ofNullable(instance)
          .map(AppBusinessModelMetaProvider::getBusinessModelMetaMap)
          .orElse(Collections.emptyMap());
    }
  }
}
