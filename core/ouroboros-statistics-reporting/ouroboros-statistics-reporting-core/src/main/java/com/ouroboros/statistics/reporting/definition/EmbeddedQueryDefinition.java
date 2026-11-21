package com.ouroboros.statistics.reporting.definition;

import java.util.List;
import java.util.Map;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.validation.DatasetDefinitionValidator;
import com.ouroboros.util.Json;

/** Private source owned and published exclusively by one composite-view model. */
public class EmbeddedQueryDefinition extends DatasetDefinition {
  private List<String> requiredContext = List.of();

  public List<String> getRequiredContext() { return requiredContext; }
  public void setRequiredContext(List<String> context) { requiredContext = context == null ? List.of() : List.copyOf(context); }

  public static EmbeddedQueryDefinition resolve(Object source, String owner) {
    return resolve(source, owner, false);
  }

  public static EmbeddedQueryDefinition resolve(Object source, String owner, boolean enforcePlatformLimits) {
    EmbeddedQueryDefinition query;
    try {
      query = Json.mapperCopy().convertValue(source, EmbeddedQueryDefinition.class);
    } catch (IllegalArgumentException error) {
      throw new BusinessException("embeddedQuery: 私有查询定义无效，" + error.getMessage());
    }
    if (query == null || query.requiredContext.isEmpty()
        || query.requiredContext.stream().anyMatch(name -> name == null || name.isBlank())) {
      throw new BusinessException("embeddedQuery.requiredContext: 请声明执行所需的账户上下文");
    }
    query.setName(owner + ".private-query");
    query.setIsEnabled(true);
    try {
      new DatasetDefinitionValidator().validate(query, enforcePlatformLimits);
    } catch (IllegalArgumentException invalid) {
      throw new BusinessException("embeddedQuery: " + invalid.getMessage(), invalid);
    }
    return query;
  }

  public void requireContext(Map<String, Object> context) {
    for (String name : requiredContext) {
      Object value = context == null ? null : context.get(name);
      if (value == null || value instanceof String text && text.isBlank()) {
        throw new BusinessException("embeddedQuery.requiredContext: 缺少账户上下文 " + name);
      }
    }
  }
}
