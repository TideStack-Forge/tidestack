package com.ouroboros.statistics.reporting.dev.service.impl;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetReferenceSummary;
import com.ouroboros.statistics.reporting.dev.service.DatasetReferenceResolver;
import com.ouroboros.statistics.reporting.validation.DatasetDefinitionValidator;

@Service
@DevelopModule("dataset")
public class DatasetDevService extends AbstractStatisticsDevService<LowcodeDatasetDefinition> {
  static final String MODEL_FILE_DIRECTORY = "statistics-reporting/datasets";
  static final String MODEL_FILE_EXTENSION = ".dataset.json";

  private final DatasetDefinitionValidator validator = new DatasetDefinitionValidator();
  private final DatasetReferenceResolver datasetReferenceResolver;

  public DatasetDevService(DatasetReferenceResolver datasetReferenceResolver) {
    this.datasetReferenceResolver = Objects.requireNonNull(datasetReferenceResolver, "datasetReferenceResolver is required");
  }

  @Override
  protected java.util.function.Predicate<LowcodeDatasetDefinition> buildSearchFilter(java.util.Map<String, Object> searchParam) {
    String keyword = java.util.Objects.toString(searchParam.get("keyword"), "").trim();
    return model -> org.apache.commons.lang3.StringUtils.containsIgnoreCase(model.getName(), keyword)
        || org.apache.commons.lang3.StringUtils.containsIgnoreCase(model.getTitle(), keyword);
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getFullName(LowcodeDatasetDefinition model) {
    return model == null ? null : model.getName();
  }

  @Override
  protected String getModelDisplayName() {
    return "数据集";
  }

  @Override
  protected Try<Void> validateModel(LowcodeDatasetDefinition model) {
    return Try.run(() -> validator.validate(model, true))
        .recoverWith(error -> Try.failure(new BusinessException(error.getMessage(), error)));
  }

  @Override
  public Try<LowcodeDatasetDefinition> update(String fullName, LowcodeDatasetDefinition model) {
    return validateStableName(fullName, model)
        .flatMap(ignore -> super.update(fullName, model));
  }

  @Override
  public Try<LowcodeDatasetDefinition> delete(String fullName, String commitMsg) {
    List<DatasetReferenceSummary> allViews = datasetReferenceResolver.resolve(fullName, false);
    if (!allViews.isEmpty()) {
      return Try.failure(new BusinessException("charts still reference dataset: " + fullName));
    }
    return super.delete(fullName, commitMsg);
  }

  private boolean sameValue(String left, String right) {
    if (left == null) {
      return right == null;
    }
    return left.equalsIgnoreCase(right);
  }

  private Try<Void> validateStableName(String fullName, LowcodeDatasetDefinition model) {
    String modelName = model == null ? null : model.getName();
    if (!sameValue(fullName, modelName)) {
      return Try.failure(new BusinessException("dataset name cannot change during update: " + fullName + " -> " + modelName));
    }
    return Try.success(null);
  }
}
