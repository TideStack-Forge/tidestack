package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.util.Json;

final class DefinitionProviderTestSupport {
  private DefinitionProviderTestSupport() {
  }

  static DatasetDefinition dataset(String name, boolean isEnabled) {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName(name);
    dataset.setTitle("Sales Dataset");
    dataset.setIsEnabled(Boolean.valueOf(isEnabled));
    dataset.setQueryDefinition(sqlQuery("select ${keyword} as id, 'Phone' as name"));
    dataset.setParameters(Collections.singletonList(parameter("keyword")));
    dataset.setSchema(schema(
        field("id", "ID", "dimension", false, false),
        field("name", "Name", "dimension", true, true)
    ));
    return dataset;
  }

  static ChartDefinition chart(String name, String datasetName, boolean isEnabled) {
    ChartDefinition chart = new ChartDefinition();
    chart.setName(name);
    chart.setDatasetName(datasetName);
    chart.setTitle("Overview");
    chart.setIsEnabled(Boolean.valueOf(isEnabled));
    chart.setChartType("metric-card");
    chart.setParams(Collections.<String, Object>singletonMap("valueFields", Collections.singletonList("id")));
    return chart;
  }

  static void writeJson(Path file, Object value) throws Exception {
    Files.createDirectories(file.getParent());
    Files.write(file, Json.toPrettyJsonString(value).getBytes(StandardCharsets.UTF_8));
  }

  static void writeText(Path file, String value) throws Exception {
    Files.createDirectories(file.getParent());
    Files.write(file, value.getBytes(StandardCharsets.UTF_8));
  }

  private static SqlQueryDefinition sqlQuery(String sql) {
    SqlQueryDefinition queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql(sql);
    return queryDefinition;
  }

  private static ParameterDefinition parameter(String name) {
    ParameterDefinition parameter = new ParameterDefinition();
    parameter.setName(name);
    parameter.setTitle("Keyword");
    parameter.setType("string");
    return parameter;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(fields));
    return schema;
  }

  private static DatasetFieldDefinition field(String name,
                                              String title,
                                              String semanticRole,
                                              boolean visibleByDefault,
                                              boolean exportable) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(title);
    field.setDataType("string");
    field.setSemanticRole(semanticRole);
    field.setVisibleByDefault(Boolean.valueOf(visibleByDefault));
    field.setExportable(Boolean.valueOf(exportable));
    return field;
  }
}
