package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatasetExecutionRequestJsonSerdeTest {

  @Test
  void shouldDeserializeDynamicFilterSortAndPage() {
    var json = "{"
        + "\"datasetName\":\"demo.sales\","
        + "\"dynamicFilter\":{"
        + "\"filter\":{\"op\":\"and\",\"children\":["
        + "{\"field\":\"region\",\"op\":\"eq\",\"value\":\"east\"},"
        + "{\"field\":\"total\",\"op\":\"in\",\"values\":[10,30]}"
        + "]},"
        + "\"sort\":[{\"field\":\"total\",\"direction\":\"desc\"}]"
        + "},"
        + "\"page\":{\"pageNo\":2,\"pageSize\":20}"
        + "}";

    DatasetExecutionRequest request = Json.toBean(DatasetExecutionRequest.class, json);

    assertEquals("demo.sales", request.getDatasetName());
    assertEquals("and", request.getDynamicFilter().getFilter().getOp());
    assertEquals(2, request.getDynamicFilter().getFilter().getChildren().size());
    assertEquals("region", request.getDynamicFilter().getFilter().getChildren().get(0).getField());
    assertEquals(Collections.singletonList(10), request.getDynamicFilter().getFilter().getChildren().get(1).getValues()
        .subList(0, 1));
    assertEquals("total", request.getDynamicFilter().getSort().get(0).getField());
    assertEquals("desc", request.getDynamicFilter().getSort().get(0).getDirection());
    assertEquals(2, request.getPage().getPageNo());
    assertEquals(20, request.getPage().getPageSize());
    assertNotNull(Json.toJsonString(request));
  }

  @Test
  void shouldCopyDynamicFilterAndPageDeeply() {
    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setDatasetName("demo.sales");

    DynamicFilterSpec dynamicFilter = new DynamicFilterSpec();
    dynamicFilter.setFilter(condition("region", "eq", "east"));
    dynamicFilter.setSort(Collections.singletonList(sort("total", "desc")));
    request.setDynamicFilter(dynamicFilter);

    PageSpec page = new PageSpec();
    page.setPageNo(1);
    page.setPageSize(50);
    request.setPage(page);

    DatasetExecutionRequest copied = request.copy();

    assertNotSame(request, copied);
    assertNotSame(request.getDynamicFilter(), copied.getDynamicFilter());
    assertNotSame(request.getDynamicFilter().getFilter(), copied.getDynamicFilter().getFilter());
    assertNotSame(request.getDynamicFilter().getSort().get(0), copied.getDynamicFilter().getSort().get(0));
    assertNotSame(request.getPage(), copied.getPage());
    assertEquals("region", copied.getDynamicFilter().getFilter().getField());
    assertEquals("desc", copied.getDynamicFilter().getSort().get(0).getDirection());
    assertEquals(50, copied.getPage().getPageSize());
  }

  private static FilterNode condition(String field, String op, Object value) {
    FilterNode node = new FilterNode();
    node.setField(field);
    node.setOp(op);
    node.setValue(value);
    return node;
  }

  private static SortSpec sort(String field, String direction) {
    SortSpec sortSpec = new SortSpec();
    sortSpec.setField(field);
    sortSpec.setDirection(direction);
    return sortSpec;
  }
}
