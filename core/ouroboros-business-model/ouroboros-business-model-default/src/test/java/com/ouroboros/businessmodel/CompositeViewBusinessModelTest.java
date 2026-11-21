package com.ouroboros.businessmodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.security.authority.Authority;

public class CompositeViewBusinessModelTest {
  private static final BusinessModelProvider EMPTY_PROVIDER = new BusinessModelProvider() {
    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.empty();
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.emptyMap();
    }
  };

  @Test
  public void shouldDeserializeCompositeViewMetaByModelType() throws Exception {
    String json = "{"
        + "\"modelType\":\"composite-view\","
        + "\"name\":\"salesList\","
        + "\"datasetName\":\"demo.sales\","
        + "\"columns\":[{\"field\":\"id\",\"title\":\"ID\"}],"
        + "\"layoutConfig\":{\"pageSize\":20}"
        + "}";

    BusinessModelMeta meta = new ObjectMapper().readValue(json, BusinessModelMeta.class);

    assertTrue(meta instanceof CompositeViewBusinessModelMeta);
  }

  @Test
  public void shouldAcceptDatasetNameSourceWithRequiredDefinition() {
    CompositeViewBusinessModel model = new CompositeViewBusinessModel(validMeta(), EMPTY_PROVIDER);

    assertTrue(model.getValidationResult().isValid());
  }

  @Test
  public void shouldProjectRuntimeAuthorityWithoutProjectingASecondWebApi() {
    CompositeViewBusinessModelMeta meta = validMeta();
    meta.setNamespace("demo");
    meta.setActionButtons(Collections.emptyList());
    CompositeViewBusinessModel model = new CompositeViewBusinessModel(meta, EMPTY_PROVIDER);

    ModelBundle bundle = model.buildModelBundle();
    Authority authority = bundle.getModel(Authority.class, "demo.salesList").orElse(null);

    assertTrue(authority != null);
    var pattern = org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse(
        authority.getApis().get(0).substring("POST ".length()));
    assertTrue(pattern.matches(org.springframework.http.server.PathContainer.parsePath(
        "/api/runtime/composite-view/DEMO.saleslist/execute")));
    assertFalse(pattern.matches(org.springframework.http.server.PathContainer.parsePath(
        "/api/runtime/composite-view/demoXsalesList/execute")));
    assertTrue(bundle.getModelMap(com.ouroboros.webapi.WebAPIModel.class).isEmpty());
  }

  @Test
  public void shouldBlockProjectionOfInvalidSourceFields() {
    var meta = validMeta();
    meta.getColumns().get(0).setField("missing");
    var model = new CompositeViewBusinessModel(meta, EMPTY_PROVIDER);
    assertFalse(model.getRelationValidationResult().isValid());
    org.junit.Assert.assertThrows(com.ouroboros.exception.BusinessException.class, model::buildModelBundle);
  }

  @Test
  public void shouldRejectMissingAndConflictingSources() {
    CompositeViewBusinessModelMeta missing = validMeta();
    missing.setDatasetName(" ");
    assertFalse(new CompositeViewBusinessModel(missing, EMPTY_PROVIDER).getValidationResult().isValid());

    CompositeViewBusinessModelMeta conflict = validMeta();
    conflict.setEmbeddedQuery(new LinkedHashMap<String, Object>());
    ValidationResult result = new CompositeViewBusinessModel(conflict, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    assertTrue(errorMessages(result).contains("composite-view.source-conflict"));
  }

  @Test
  public void shouldRejectInvalidColumnsFiltersButtonsAndLayout() {
    CompositeViewBusinessModelMeta meta = validMeta();
    meta.getColumns().get(0).setField(" ");
    meta.getFilterConfig().setItems(Collections.singletonList(filter("missingField", "bad-op", "custom")));
    meta.getActionButtons().get(0).setApiRef(" ");
    meta.getLayoutConfig().setPageSize(0);

    ValidationResult result = new CompositeViewBusinessModel(meta, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    String messages = errorMessages(result);
    assertTrue(messages.contains("dataset.field-not-allowed"));
    assertTrue(messages.contains("dataset.filter-op-not-allowed"));
    assertTrue(messages.contains("composite-view.action-api-invalid"));
    assertTrue(messages.contains("dataset.page-invalid"));
  }

  @Test
  public void shouldOnlyAcceptOperatorsSupportedByDatasetExecution() {
    for (String operator : java.util.List.of("eq", "neq", "gt", "gte", "lt", "lte", "in", "notIn", "like", "isNull", "isNotNull")) {
      var meta = validMeta();
      meta.getFilterConfig().getItems().get(0).setOps(java.util.List.of(operator));
      assertTrue(new CompositeViewBusinessModel(meta, EMPTY_PROVIDER).getValidationResult().isValid());
    }
    for (String operator : java.util.List.of("ne", "contains", "startsWith", "endsWith", "between")) {
      var meta = validMeta();
      meta.getFilterConfig().getItems().get(0).setOps(java.util.List.of(operator));
      var model = new CompositeViewBusinessModel(meta, EMPTY_PROVIDER);
      assertFalse(model.getValidationResult().isValid());
      org.junit.Assert.assertThrows(com.ouroboros.exception.BusinessException.class, model::buildModelBundle);
    }
  }

  private CompositeViewBusinessModelMeta validMeta() {
    CompositeViewBusinessModelMeta meta = new CompositeViewBusinessModelMeta();
    meta.setName("salesList");
    meta.setTitle("销售列表");
    meta.setDatasetName("demo.sales");
    meta.setColumns(Collections.singletonList(column("id", "ID")));
    meta.setFilterConfig(filterConfig());
    meta.setActionButtons(Collections.singletonList(button()));
    meta.setLayoutConfig(layoutConfig());
    return meta;
  }

  private CompositeViewBusinessModelMeta.ColumnDef column(String field, String title) {
    CompositeViewBusinessModelMeta.ColumnDef column = new CompositeViewBusinessModelMeta.ColumnDef();
    column.setField(field);
    column.setTitle(title);
    column.setFormat("text");
    return column;
  }

  private CompositeViewBusinessModelMeta.FilterConfig filterConfig() {
    CompositeViewBusinessModelMeta.FilterConfig config = new CompositeViewBusinessModelMeta.FilterConfig();
    config.setItems(Collections.singletonList(filter("id", "eq", "input")));
    return config;
  }

  private CompositeViewBusinessModelMeta.FilterItemDef filter(String field, String op, String widget) {
    CompositeViewBusinessModelMeta.FilterItemDef item = new CompositeViewBusinessModelMeta.FilterItemDef();
    item.setField(field);
    item.setOps(Collections.singletonList(op));
    item.setWidget(widget);
    return item;
  }

  private CompositeViewBusinessModelMeta.ActionButtonDef button() {
    CompositeViewBusinessModelMeta.ActionButtonDef button = new CompositeViewBusinessModelMeta.ActionButtonDef();
    button.setLabel("查看");
    button.setScope("row");
    button.setApiRef("demo.sales.view");
    button.setContextBinding(Collections.singletonMap("id", "id"));
    return button;
  }

  private CompositeViewBusinessModelMeta.LayoutConfig layoutConfig() {
    CompositeViewBusinessModelMeta.LayoutConfig layout = new CompositeViewBusinessModelMeta.LayoutConfig();
    layout.setPageSize(20);
    CompositeViewBusinessModelMeta.SortDef sort = new CompositeViewBusinessModelMeta.SortDef();
    sort.setField("id");
    sort.setDirection("asc");
    layout.setDefaultSort(Arrays.asList(sort));
    return layout;
  }

  private String errorMessages(ValidationResult result) {
    StringBuilder builder = new StringBuilder();
    for (Throwable error : result.getErrors()) {
      builder.append(error.getMessage()).append('\n');
    }
    return builder.toString();
  }
}
