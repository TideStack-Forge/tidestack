package com.ouroboros.businessmodel.dev.service.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelBuilder;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationDiagnostic;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationSnapshot;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationState;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.validation.ValidationResult;

public class BusinessModelValidationSnapshotSupport {
  private static final String DEFAULT_MESSAGE = "未知业务模型校验错误";
  private final List<BusinessModelBuilder> builders;
  private final BusinessModelProvider provider;

  public BusinessModelValidationSnapshotSupport(List<BusinessModelBuilder> builders, BusinessModelProvider provider) {
    this.builders = builders == null ? Collections.emptyList() : new ArrayList<>(builders);
    this.provider = provider;
  }

  public BusinessModelValidationSnapshot snapshot(BusinessModelMeta meta) {
    if (meta == null) {
      return buildSnapshot(Collections.emptyList());
    }

    var businessModel = builders.stream()
        .filter(builder -> Boolean.TRUE.equals(builder.support(meta)))
        .findFirst()
        .map(builder -> (BusinessModel) builder.build(meta, provider))
        .orElse(null);

    if (businessModel == null) {
      return buildSnapshot(Collections.singletonList(new BusinessModelValidationDiagnostic(
          "unsupported_business_model_type",
          "不支持的业务模型类型: " + safeModelType(meta),
          "model"
      )));
    }

    return snapshotModel(businessModel);
  }

  public BusinessModelValidationSnapshot snapshotModel(BusinessModel businessModel) {
    var diagnostics = new ArrayList<BusinessModelValidationDiagnostic>();
    diagnostics.addAll(toDiagnostics(businessModel::getValidationResult, "model"));
    diagnostics.addAll(toDiagnostics(businessModel::getRelationValidationResult, "relation"));
    return buildSnapshot(diagnostics);
  }

  public BusinessModelValidationSnapshot failure(Throwable error, String scope) {
    return buildSnapshot(List.of(toDiagnostic(error, scope)));
  }

  private BusinessModelValidationSnapshot buildSnapshot(List<BusinessModelValidationDiagnostic> diagnostics) {
    var validationErrors = diagnostics.stream()
        .map(BusinessModelValidationDiagnostic::getMessage)
        .collect(Collectors.toList());
    return new BusinessModelValidationSnapshot(
        diagnostics.isEmpty() ? BusinessModelValidationState.VALID : BusinessModelValidationState.INVALID,
        validationErrors.isEmpty() ? null : validationErrors.get(0),
        validationErrors,
        diagnostics,
        LocalDateTime.now()
    );
  }

  private List<BusinessModelValidationDiagnostic> toDiagnostics(Supplier<ValidationResult> validationResultSupplier, String scope) {
    try {
      return toDiagnostics(validationResultSupplier.get(), scope);
    } catch (RuntimeException error) {
      return Collections.singletonList(toDiagnostic(error, scope));
    }
  }

  private List<BusinessModelValidationDiagnostic> toDiagnostics(ValidationResult validationResult, String scope) {
    if (validationResult == null || validationResult.getErrors().isEmpty()) {
      return Collections.emptyList();
    }
    return validationResult.getErrors().stream()
        .map(error -> toDiagnostic(error, scope))
        .collect(Collectors.toList());
  }

  private BusinessModelValidationDiagnostic toDiagnostic(Throwable error, String scope) {
    return new BusinessModelValidationDiagnostic(diagnosticKind(error), message(error), scope);
  }

  private String diagnosticKind(Throwable error) {
    if (error == null || error.getClass() == null) {
      return "unknown_diagnostic";
    }
    return toSnakeCase(error.getClass().getSimpleName().replaceFirst("Exception$", ""));
  }

  private String message(Throwable error) {
    if (error == null) {
      return DEFAULT_MESSAGE;
    }
    var message = error.getMessage();
    if (message != null && !message.trim().isEmpty()) {
      return message;
    }
    var className = error.getClass().getSimpleName();
    if (className != null && !className.trim().isEmpty()) {
      return className;
    }
    return DEFAULT_MESSAGE;
  }

  private String safeModelType(BusinessModelMeta meta) {
    var modelType = meta.getModelType();
    return modelType == null || modelType.trim().isEmpty() ? "unknown" : modelType;
  }

  private String toSnakeCase(String value) {
    if (value == null || value.isEmpty()) {
      return "unknown_diagnostic";
    }
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .toLowerCase(Locale.ROOT);
  }
}
