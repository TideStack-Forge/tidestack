package com.ouroboros.app.statistics;

import org.springframework.stereotype.Component;
import com.ouroboros.configuration.validation.ConfigurationValueValidator;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicy;
import com.ouroboros.util.Json;

@Component
public class StatisticsExecutionPolicyValidator implements ConfigurationValueValidator {
  @Override
  public boolean supports(String key) { return DatasetExecutionPolicy.KEY.equals(key); }

  @Override
  public void validate(Object value) {
    Object policy = value instanceof String text
        ? Json.tryToBean(Object.class, text).getOrElseThrow(error -> new com.ouroboros.exception.BusinessException("执行限制配置无效", error))
        : value;
    DatasetExecutionPolicy.parse(policy, "validation");
  }
}
