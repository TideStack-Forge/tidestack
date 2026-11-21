package com.ouroboros.businessmodel.action;

import java.util.Map;
import java.util.Objects;

/** Physical query identities resolved by the same feature projection that creates the data models. */
public record BusinessActionFeatureQueryPlan(Kind kind, String sourceModel, String queryModel,
                                           String sourceKeyField, String businessKeyField,
                                           Map<String, String> sourceFieldMappings,
                                           Map<String, Object> fixedConditions) {
  public static final String HANDLER_NAME = "businessModelFeatureQueryHandler";

  public enum Kind {
    DELETED("searchDeleted", "查询已删除记录"), ARCHIVE("searchArchive", "查询归档记录"), HISTORY("searchHistory", "查询历史版本");

    private final String actionName;
    private final String title;
    Kind(String actionName, String title) { this.actionName = actionName; this.title = title; }
    public String actionName() { return actionName; }
    public String title() { return title; }
  }

  public BusinessActionFeatureQueryPlan {
    Objects.requireNonNull(kind);
    for (String name : new String[]{sourceModel, queryModel, sourceKeyField, businessKeyField}) {
      if (name == null || name.isBlank()) { throw new IllegalArgumentException("Feature query identity must be complete"); }
    }
    sourceFieldMappings = Map.copyOf(sourceFieldMappings);
    fixedConditions = Map.copyOf(fixedConditions);
    if (!businessKeyField.equals(sourceFieldMappings.get(sourceKeyField))) {
      throw new IllegalArgumentException("Feature query must map its source business key");
    }
  }
}
