package com.ouroboros.statistics.reporting.runtime.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.CompositeViewBusinessModel;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.PageSpec;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.web.WebApiResult;

class CompositeViewExecutionControllerTest {
  private Map<String, BusinessModel> originalModels;

  @BeforeEach
  void rememberModels() throws Exception {
    originalModels = readBusinessModelMap();
  }

  @AfterEach
  void restoreModels() throws Exception {
    writeBusinessModelMap(originalModels);
  }

  @Test
  void shouldExecuteDatasetNamePathThroughDatasetExecutionService() throws Exception {
    AtomicReference<DatasetExecutionRequest> capturedRequest = new AtomicReference<DatasetExecutionRequest>();
    CompositeViewExecutionController controller = new CompositeViewExecutionController(
        request -> {
          capturedRequest.set(request);
          DatasetResult result = new DatasetResult();
          result.setRows(Collections.singletonList(row("id", "A-1")));
          result.setPageInfo(Collections.<String, Object>singletonMap("total", 1L));
          return result;
        },
        request -> Collections.<String, Object>singletonMap("roles", Collections.singletonList("trusted-role"))
    );
    installCompositeView("demo.sales.list", "demo.sales");

    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setDatasetName("client.override");
    request.setExecutionContext(Collections.<String, Object>singletonMap("roles", Collections.singletonList("guest")));
    request.setPage(page(1, 20));

    WebApiResult<DatasetResult> response = controller.execute("demo.sales.list", request);

    assertEquals(0, response.getCode());
    assertEquals(1L, response.getData().getPageInfo().get("total"));
    assertNotSame(request, capturedRequest.get());
    assertEquals("demo.sales", capturedRequest.get().getDatasetName());
    assertEquals("demo.sales.list", capturedRequest.get().getCompositeViewName());
    assertEquals(Collections.singletonList("trusted-role"), capturedRequest.get().getExecutionContext().get("roles"));
    assertEquals(Collections.singletonList("guest"), request.getExecutionContext().get("roles"));
  }

  @Test
  void shouldApplyConfiguredPageDefaultsBeforeDatasetExecution() throws Exception {
    AtomicInteger invocationCount = new AtomicInteger();
    CompositeViewExecutionController controller = new CompositeViewExecutionController(
        request -> {
          invocationCount.incrementAndGet();
          return new DatasetResult();
        },
        request -> Collections.<String, Object>emptyMap()
    );
    installCompositeView("demo.sales.list", "demo.sales");

    WebApiResult<DatasetResult> response = controller.execute("demo.sales.list", new DatasetExecutionRequest());

    assertEquals(0, response.getCode());
    assertEquals(1, invocationCount.get());
  }

  @Test
  void shouldReturnObservableErrorWhenViewIsMissing() {
    CompositeViewExecutionController controller = new CompositeViewExecutionController(
        request -> new DatasetResult(),
        request -> Collections.<String, Object>emptyMap()
    );

    PlatformException error = assertThrows(PlatformException.class,
        () -> controller.execute("demo.missing", requestWithPage()));

    assertTrue(error.getMessage().contains("composite-view.not-found"));
  }

  @Test
  void shouldSurfaceDatasetPipelineAuthorizationFailure() throws Exception {
    CompositeViewExecutionController controller = new CompositeViewExecutionController(
        request -> {
          throw new IllegalArgumentException("permission denied");
        },
        request -> Collections.<String, Object>emptyMap()
    );
    installCompositeView("demo.sales.list", "demo.sales");

    PlatformException error = assertThrows(PlatformException.class,
        () -> controller.execute("demo.sales.list", requestWithPage()));

    assertEquals("permission denied", error.getMessage());
  }

  @Test
  void shouldApplySortProjectColumnsAndPreserveCallerRequest() throws Exception {
    var view = installCompositeView("demo.sales.list", "demo.sales");
    var order = new CompositeViewBusinessModelMeta.SortDef();
    order.setField("id");
    order.setDirection("desc");
    view.getLayoutConfig().setDefaultSort(java.util.List.of(order));
    AtomicReference<DatasetExecutionRequest> captured = new AtomicReference<>();
    var controller = new CompositeViewExecutionController(request -> {
      captured.set(request);
      var result = new DatasetResult();
      result.setRows(java.util.List.of(Map.of("ID", "one", "hidden", "private")));
      var schema = new com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition();
      var id = new com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition();
      id.setName("id");
      var hidden = new com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition();
      hidden.setName("hidden");
      schema.setFields(java.util.List.of(id, hidden));
      result.setResolvedSchema(schema);
      return result;
    }, request -> Map.of());
    var request = new DatasetExecutionRequest();
    var response = controller.execute("demo.sales.list", request).getData();
    assertEquals(20, captured.get().getPage().getPageSize());
    assertEquals("desc", captured.get().getDynamicFilter().getSort().getFirst().getDirection());
    assertEquals(java.util.List.of(Map.of("id", "one")), response.getRows());
    assertEquals(java.util.List.of("id"), response.getResolvedSchema().getFields().stream().map(field -> field.getName()).toList());
    org.junit.jupiter.api.Assertions.assertNull(request.getPage());
    org.junit.jupiter.api.Assertions.assertNull(request.getDynamicFilter());
  }

  @Test
  void shouldEnforceDeclaredFiltersAndSortsBeforeCallingDataset() throws Exception {
    var view = installCompositeView("demo.sales.list", "demo.sales");
    var item = new CompositeViewBusinessModelMeta.FilterItemDef();
    item.setField("id");
    item.setOps(java.util.List.of("eq"));
    view.getFilterConfig().setItems(java.util.List.of(item));
    var count = new AtomicInteger();
    var controller = new CompositeViewExecutionController(request -> {
      count.incrementAndGet();
      return new DatasetResult();
    }, request -> Map.of());
    var request = new DatasetExecutionRequest();
    var filter = new com.ouroboros.statistics.reporting.runtime.execution.DynamicFilterSpec();
    var node = new com.ouroboros.statistics.reporting.runtime.execution.FilterNode();
    node.setField("ID");
    node.setOp("eq");
    node.setValue("one");
    filter.setFilter(node);
    request.setDynamicFilter(filter);
    controller.execute("demo.sales.list", request);
    assertEquals(1, count.get());
    node.setOp("contains");
    var invalidOperator = assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> controller.execute("demo.sales.list", request));
    assertTrue(invalidOperator.getMessage().contains("dataset.filter-op-not-allowed"));
    node.setOp("eq");
    node.setField("hidden");
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.execute("demo.sales.list", request));
    filter.setFilter(null);
    var order = new com.ouroboros.statistics.reporting.runtime.execution.SortSpec();
    order.setField("hidden");
    order.setDirection("asc");
    filter.setSort(java.util.List.of(order));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.execute("demo.sales.list", request));
    assertEquals(1, count.get());
  }

  private CompositeViewBusinessModelMeta installCompositeView(String fullName, String datasetName) throws Exception {
    CompositeViewBusinessModelMeta meta = new CompositeViewBusinessModelMeta();
    int dot = fullName.lastIndexOf('.');
    meta.setNamespace(dot < 0 ? "" : fullName.substring(0, dot));
    meta.setName(dot < 0 ? fullName : fullName.substring(dot + 1));
    meta.setTitle(fullName);
    meta.setIsEnabled(true);
    meta.setDatasetName(datasetName);
    meta.setColumns(Collections.singletonList(column("id")));
    CompositeViewBusinessModelMeta.LayoutConfig layout = new CompositeViewBusinessModelMeta.LayoutConfig();
    layout.setPageSize(20);
    meta.setLayoutConfig(layout);

    Map<String, BusinessModel> models = new TreeMap<String, BusinessModel>(String.CASE_INSENSITIVE_ORDER);
    models.put(fullName, new CompositeViewBusinessModel(meta, emptyProvider()));
    writeBusinessModelMap(models);
    return meta;
  }

  private CompositeViewBusinessModelMeta.ColumnDef column(String fieldName) {
    CompositeViewBusinessModelMeta.ColumnDef column = new CompositeViewBusinessModelMeta.ColumnDef();
    column.setField(fieldName);
    column.setTitle(fieldName);
    return column;
  }

  private DatasetExecutionRequest requestWithPage() {
    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setPage(page(1, 20));
    return request;
  }

  private PageSpec page(int pageNo, int pageSize) {
    PageSpec page = new PageSpec();
    page.setPageNo(pageNo);
    page.setPageSize(pageSize);
    return page;
  }

  private Map<String, Object> row(String key, Object value) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put(key, value);
    return row;
  }

  private BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      @Override
      public java.util.Optional<BusinessModel> getBusinessModel(String fullName) {
        return java.util.Optional.empty();
      }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() {
        return Collections.emptyMap();
      }
    };
  }

  private Map<String, BusinessModel> readBusinessModelMap() throws Exception {
    Field field = BusinessModelCenter.class.getDeclaredField("businessModelMap");
    field.setAccessible(true);
    Object value = field.get(null);
    if (value == null) {
      return new TreeMap<String, BusinessModel>(String.CASE_INSENSITIVE_ORDER);
    }
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalStateException("BusinessModelCenter.businessModelMap must be a map");
    }
    Map<String, BusinessModel> models = new TreeMap<String, BusinessModel>(String.CASE_INSENSITIVE_ORDER);
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
      if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof BusinessModel)) {
        throw new IllegalStateException("BusinessModelCenter.businessModelMap contains an invalid entry");
      }
      models.put((String) entry.getKey(), (BusinessModel) entry.getValue());
    }
    return models;
  }

  private void writeBusinessModelMap(Map<String, BusinessModel> models) throws Exception {
    Field field = BusinessModelCenter.class.getDeclaredField("businessModelMap");
    field.setAccessible(true);
    field.set(null, models);
  }
}
