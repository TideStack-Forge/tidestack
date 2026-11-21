package com.ouroboros.statistics.reporting.runtime.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetExecutionControllerTest {

  @Test
  void shouldExecuteDatasetWithoutChartProjectionConcerns() {
    DatasetDefinition dataset = enabledDataset();
    DatasetResult datasetResult = datasetResult(dataset.getSchema(), Collections.singletonList(
        row("id", "A-1", "name", "Phone", "secret", "hidden")));
    AtomicReference<DatasetExecutionRequest> capturedRequest = new AtomicReference<DatasetExecutionRequest>();
    DatasetExecutionController controller = new DatasetExecutionController(
        datasetCenter(dataset),
        request -> {
          capturedRequest.set(request);
          return datasetResult;
        },
        (loadedDataset, request) -> true
    );

    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setDatasetName(dataset.getName());

    WebApiResult<DatasetResult> response = controller.execute(request.getDatasetName(), request);

    assertEquals(0, response.getCode());
    assertEquals(datasetResult, response.getData());
    assertEquals(dataset.getName(), capturedRequest.get().getDatasetName());
  }

  @Test
  void shouldRejectExportBeforeExecutingWhenPermissionCheckFails() {
    DatasetDefinition dataset = enabledDataset();
    AtomicInteger invocationCount = new AtomicInteger();
    DatasetExecutionController controller = new DatasetExecutionController(
        datasetCenter(dataset),
        request -> {
          invocationCount.incrementAndGet();
          return datasetResult(dataset.getSchema(), Collections.<Map<String, Object>>emptyList());
        },
        (loadedDataset, request) -> false
    );

    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setDatasetName(dataset.getName());

    BusinessException error = assertThrows(BusinessException.class, () -> controller.export(request.getDatasetName(), request));

    assertEquals("export permission denied", error.getMessage());
    assertEquals(0, invocationCount.get());
  }

  @Test
  void shouldReuseExecutionServiceAndFilterExportableColumns() {
    DatasetDefinition dataset = enabledDataset();
    AtomicInteger invocationCount = new AtomicInteger();
    DatasetExecutionController controller = new DatasetExecutionController(
        datasetCenter(dataset),
        request -> {
          invocationCount.incrementAndGet();
          return datasetResult(dataset.getSchema(), Collections.singletonList(
              row("id", "A-1", "name", "Phone", "secret", "hidden")));
        },
        (loadedDataset, request) -> true
    );

    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setDatasetName(dataset.getName());

    WebApiResult<Map<String, Object>> response = controller.export(request.getDatasetName(), request);

    assertEquals(0, response.getCode());
    assertEquals(1, invocationCount.get());
    assertEquals(Collections.singletonList("name"), extractNames(mapList(response.getData(), "columns")));
    Map<String, Object> exportedRow = mapList(response.getData(), "rows").get(0);
    assertEquals("Phone", exportedRow.get("name"));
    assertFalse(exportedRow.containsKey("id"));
    assertFalse(exportedRow.containsKey("secret"));
  }

  @Test
  void shouldReplaceClientExecutionContextBeforeExecutingDataset() {
    DatasetDefinition dataset = enabledDataset();
    AtomicReference<DatasetExecutionRequest> capturedRequest = new AtomicReference<DatasetExecutionRequest>();
    DatasetExecutionController controller = new DatasetExecutionController(
        datasetCenter(dataset),
        request -> {
          capturedRequest.set(request);
          return datasetResult(dataset.getSchema(), Collections.<Map<String, Object>>emptyList());
        },
        (loadedDataset, request) -> true,
        request -> Collections.<String, Object>singletonMap(
            "roles", Collections.singletonList("trusted-role"))
    );
    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setDatasetName("client.other");
    request.setExecutionContext(new LinkedHashMap<String, Object>(Collections.<String, Object>singletonMap(
        "roles", Collections.singletonList("guest"))));

    WebApiResult<DatasetResult> response = controller.execute("DEMO.SALES", request);

    assertEquals(0, response.getCode());
    assertNotSame(request, capturedRequest.get());
    assertEquals("DEMO.SALES", capturedRequest.get().getDatasetName());
    assertEquals("client.other", request.getDatasetName());
    assertEquals(Collections.singletonList("trusted-role"), capturedRequest.get().getExecutionContext().get("roles"));
    assertEquals(Collections.singletonList("guest"), request.getExecutionContext().get("roles"));
  }

  private static DatasetDefinition enabledDataset() {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    dataset.setSchema(schema(
        field("id", "ID", false),
        field("name", "Name", true),
        field("secret", "Secret", false)
    ));
    return dataset;
  }

  private static DatasetFieldDefinition field(String name, String title, boolean exportable) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(title);
    field.setExportable(Boolean.valueOf(exportable));
    return field;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(fields));
    return schema;
  }

  private static DatasetResult datasetResult(DatasetSchemaDefinition schema, List<Map<String, Object>> rows) {
    DatasetResult result = new DatasetResult();
    result.setResolvedSchema(schema);
    result.setRows(rows);
    return result;
  }

  private static Map<String, Object> row(Object... values) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      row.put((String) values[index], values[index + 1]);
    }
    return row;
  }

  private static DefaultDatasetDefinitionCenter datasetCenter(DatasetDefinition dataset) {
    return new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(
        () -> Collections.singletonList(dataset)));
  }

  private static List<String> extractNames(List<Map<String, Object>> items) {
    return items.stream().map(item -> (String) item.get("name")).toList();
  }

  private static List<Map<String, Object>> mapList(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    if (!(value instanceof List<?>)) {
      throw new IllegalStateException("property must be a list: " + key);
    }
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Object item : (List<?>) value) {
      if (!(item instanceof Map<?, ?>)) {
        throw new IllegalStateException("list item must be a map: " + key);
      }
      items.add(stringKeyMap((Map<?, ?>) item));
    }
    return items;
  }

  private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
    Map<String, Object> target = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (!(entry.getKey() instanceof String)) {
        throw new IllegalStateException("map key must be a string");
      }
      target.put((String) entry.getKey(), entry.getValue());
    }
    return target;
  }
}
