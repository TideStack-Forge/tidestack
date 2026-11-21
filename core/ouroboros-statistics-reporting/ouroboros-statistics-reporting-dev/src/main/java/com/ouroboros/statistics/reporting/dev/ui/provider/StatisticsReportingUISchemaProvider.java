package com.ouroboros.statistics.reporting.dev.ui.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

public class StatisticsReportingUISchemaProvider implements UISchemaProvider {
  static final String DATASET_SCHEMA_PATH = "/dev/{appName}/dataset";
  static final String DATASET_ADD_SCHEMA_PATH = "/dev/{appName}/dataset/add";
  static final String DATASET_EDIT_SCHEMA_PATH = "/dev/{appName}/dataset/{datasetName}";
  static final String CHART_SCHEMA_PATH = "/dev/{appName}/chart";
  static final String CHART_ADD_SCHEMA_PATH = "/dev/{appName}/chart/add";
  static final String CHART_EDIT_SCHEMA_PATH = "/dev/{appName}/chart/{fullName}";
  private static final String DATASET_RESOURCE =
      "META-INF/ouroboros/ui-schema/dev/{appName}/dataset.json";
  private static final String DATASET_ADD_RESOURCE =
      "META-INF/ouroboros/ui-schema/dev/{appName}/dataset/add.json";
  private static final String DATASET_EDIT_RESOURCE =
      "META-INF/ouroboros/ui-schema/dev/{appName}/dataset/{datasetName}.json";
  private static final String CHART_RESOURCE =
      "META-INF/ouroboros/ui-schema/dev/{appName}/chart.json";
  private static final String CHART_ADD_RESOURCE =
      "META-INF/ouroboros/ui-schema/dev/{appName}/chart/add.json";
  private static final String CHART_EDIT_RESOURCE =
      "META-INF/ouroboros/ui-schema/dev/{appName}/chart/{fullName}.json";

  @Override
  public Optional<Object> getUISchema(String path) {
    return Optional.ofNullable(getUISchemaMap().get(path));
  }

  @Override
  public Map<String, Object> getUISchemaMap() {
    Map<String, Object> schemaMap = new LinkedHashMap<String, Object>();
    schemaMap.put(DATASET_SCHEMA_PATH, schema(DATASET_RESOURCE));
    schemaMap.put(DATASET_ADD_SCHEMA_PATH, schema(DATASET_ADD_RESOURCE));
    schemaMap.put(DATASET_EDIT_SCHEMA_PATH, schema(DATASET_EDIT_RESOURCE));
    schemaMap.put(CHART_SCHEMA_PATH, schema(CHART_RESOURCE));
    schemaMap.put(CHART_ADD_SCHEMA_PATH, schema(CHART_ADD_RESOURCE));
    schemaMap.put(CHART_EDIT_SCHEMA_PATH, schema(CHART_EDIT_RESOURCE));
    return schemaMap;
  }

  private Map<String, Object> schema(String resourcePath) {
    return ResourceUtil.getResourceContentStream(resourcePath)
        .findFirst()
        .map(Json::toMap)
        .orElse(Collections.<String, Object>emptyMap());
  }
}
