package com.ouroboros.statistics.reporting.dev.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;

abstract class AbstractChartDevService<V extends ChartDefinition> extends AbstractStatisticsDevService<V> {
  private final DatasetDevService datasetDevService;
  private final ChartDefinitionValidator validator = new ChartDefinitionValidator();
  private final String modelFileDirectory;
  private final String modelFileExtension;
  private final String modelDisplayName;

  protected AbstractChartDevService(DatasetDevService datasetDevService,
                                          String modelFileDirectory,
                                          String modelFileExtension,
                                          String modelDisplayName) {
    this.datasetDevService = datasetDevService;
    this.modelFileDirectory = modelFileDirectory;
    this.modelFileExtension = modelFileExtension;
    this.modelDisplayName = modelDisplayName;
  }

  @Override
  protected java.util.function.Predicate<V> buildSearchFilter(java.util.Map<String, Object> searchParam) {
    String keyword = java.util.Objects.toString(searchParam.get("keyword"), "").trim();
    return model -> org.apache.commons.lang3.StringUtils.containsIgnoreCase(model.getName(), keyword)
        || org.apache.commons.lang3.StringUtils.containsIgnoreCase(model.getTitle(), keyword);
  }

  @Override
  protected String getModelFileDirectory() {
    return modelFileDirectory;
  }

  @Override
  protected String getModelFileExtension() {
    return modelFileExtension;
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getFullName(V model) {
    return model == null ? null : model.getName();
  }

  @Override
  protected String getModelDisplayName() {
    return modelDisplayName;
  }

  protected abstract TypeReference<V> getModelTypeReference();

  @Override
  public Try<V> buildModelFromFileContent(String fileContent) {
    TypeReference<V> modelTypeReference = getModelTypeReference();
    if (modelTypeReference != null) {
      return Json.tryToStrictBean(modelTypeReference, fileContent);
    }
    return super.buildModelFromFileContent(fileContent);
  }

  @Override
  protected Try<Void> validateModel(V model) {
    return Try.run(() -> {
      if (model == null || model.getDatasetName() == null || model.getDatasetName().trim().isEmpty()) {
        throw new IllegalArgumentException("datasetName is required");
      }
      LowcodeDatasetDefinition dataset = datasetDevService.getOne(model.getDatasetName())
          .orElseThrow(() -> new IllegalArgumentException("dataset definition not found: " + model.getDatasetName()));
      validator.validate(dataset, model);
      if (Boolean.TRUE.equals(model.getIsEnabled())
          && !Boolean.TRUE.equals(dataset.getIsEnabled())) {
        throw new IllegalArgumentException("enabled dataset is required for enabled view: " + model.getDatasetName());
      }
    }).recoverWith(error -> Try.failure(new BusinessException(error.getMessage(), error)));
  }

  @Override
  public Try<V> update(String fullName, V model) {
    return validateStableName(fullName, model)
        .flatMap(ignore -> super.update(fullName, model));
  }

  private Try<Void> validateStableName(String fullName, V model) {
    String modelName = getFullName(model);
    if (fullName == null ? modelName != null : !fullName.equalsIgnoreCase(modelName)) {
      return Try.failure(new BusinessException("view name cannot change during update: " + fullName + " -> " + modelName));
    }
    return Try.success(null);
  }
}
