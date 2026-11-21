package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;

public class DefaultDatasetParameterNormalizer implements DatasetParameterNormalizer {
  public static final String OMITTED_PARAMETERS_CONTEXT_KEY = "__statisticsReportingOmittedParameters";

  public DatasetExecutionRequest normalize(DatasetDefinition dataset, DatasetExecutionRequest request) {
    if (dataset == null) {
      throw new IllegalArgumentException("dataset definition is required");
    }
    if (request == null) {
      throw new IllegalArgumentException("dataset execution request is required");
    }

    DatasetExecutionRequest normalized = request.copy();
    Map<String, Object> appliedParameters = new LinkedHashMap<>();
    Map<String, Object> normalizedExecutionContext = normalized.getExecutionContext() == null
        ? new LinkedHashMap<String, Object>()
        : normalized.getExecutionContext();
    Map<String, Object> runtimeParameters = request.getRuntimeParameters() == null
        ? new LinkedHashMap<String, Object>()
        : request.getRuntimeParameters();
    java.util.List<String> omittedParameters = new java.util.ArrayList<String>();

    if (dataset.getParameters() != null) {
      for (ParameterDefinition parameter : dataset.getParameters()) {
        if (parameter == null || isBlank(parameter.getName())) {
          continue;
        }
        boolean systemContextValue = "systemContext".equals(parameter.getValueSource())
            || !isBlank(parameter.getSystemContextBinding());
        Object value = systemContextValue
            ? normalizedExecutionContext.get(parameter.getSystemContextBinding())
            : runtimeParameters.get(parameter.getName());
        if (systemContextValue && isEmpty(value)) {
          if (parameter.isRequired()) {
            throw new IllegalArgumentException(
                "missing required system context parameter: " + parameter.getName());
          }
          if (parameter.getDefaultValue() == null) {
            omittedParameters.add(parameter.getName());
            appliedParameters.put(parameter.getName(), null);
            continue;
          }
        }
        if (!isEmpty(value)) {
          appliedParameters.put(parameter.getName(), value);
          continue;
        }
        if (parameter.getDefaultValue() != null) {
          appliedParameters.put(parameter.getName(), parameter.getDefaultValue());
          continue;
        }
        if (parameter.isRequired()) {
          throw new IllegalArgumentException("missing required parameter: " + parameter.getName());
        }
        if (!parameter.isAllowEmptyOmitCondition() && runtimeParameters.containsKey(parameter.getName())) {
          appliedParameters.put(parameter.getName(), value);
        } else {
          appliedParameters.put(parameter.getName(), null);
          omittedParameters.add(parameter.getName());
        }
      }
    }

    normalizedExecutionContext.put(OMITTED_PARAMETERS_CONTEXT_KEY, omittedParameters);
    normalized.setExecutionContext(normalizedExecutionContext);
    normalized.setAppliedParameters(appliedParameters);
    return normalized;
  }

  private boolean isEmpty(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof String) {
      return ((String) value).trim().isEmpty();
    }
    if (value instanceof Collection) {
      return ((Collection<?>) value).isEmpty();
    }
    if (value instanceof Map) {
      return ((Map<?, ?>) value).isEmpty();
    }
    return false;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
