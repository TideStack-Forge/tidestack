package com.ouroboros.businessmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIModel;

public class DashboardBusinessModelTest {
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
  public void shouldDeserializeDashboardMetaByModelType() throws Exception {
    String json = "{"
        + "\"modelType\":\"dashboard\","
        + "\"name\":\"salesDashboard\","
        + "\"layout\":{\"cols\":24,\"rowHeight\":30},"
        + "\"widgets\":[{"
        + "\"id\":\"salesChart\","
        + "\"widgetName\":\"chart\","
        + "\"chartName\":\"demo.salesChart\","
        + "\"position\":{\"x\":0,\"y\":0},"
        + "\"size\":{\"cols\":12,\"rows\":10},"
        + "\"config\":{"
        + "\"title\":\"销售趋势\",\"showHeader\":true,\"showBorder\":true,"
        + "\"border\":{\"top-border-width\":\"1px\"},"
        + "\"showRadius\":true,"
        + "\"borderRadius\":{\"top-left-border-radius\":\"4px\"},"
        + "\"showShadow\":true,\"boxShadow\":\"0 1px 2px #000\","
        + "\"bodyPadding\":\"16px\",\"backgroundColor\":\"#fff\","
        + "\"headerBackgroundColor\":\"#f5f5f5\"}"
        + "}]"
        + "}";

    BusinessModelMeta meta = new ObjectMapper().readValue(json, BusinessModelMeta.class);

    assertTrue(meta instanceof DashboardBusinessModelMeta);
    DashboardBusinessModelMeta.WidgetConfig config =
        ((DashboardBusinessModelMeta) meta).getWidgets().get(0).getConfig();
    assertEquals("销售趋势", config.getTitle());
    assertEquals("1px", config.getBorder().get("top-border-width"));
    assertEquals("4px", config.getBorderRadius().get("top-left-border-radius"));
    assertEquals("16px", config.getBodyPadding());
  }

  @Test
  public void shouldRejectLegacyDashboardMetadataProperty() throws Exception {
    String json = "{"
        + "\"modelType\":\"dashboard\","
        + "\"name\":\"salesDashboard\","
        + "\"migration\":{\"legacyDashboardName\":\"legacy-sales\"}"
        + "}";

    try {
      new ObjectMapper().readValue(json, BusinessModelMeta.class);
    } catch (JsonMappingException error) {
      assertTrue(error.getCause() instanceof IllegalArgumentException);
      assertTrue(error.getCause().getMessage().contains("migration"));
      return;
    }
    throw new AssertionError("legacy dashboard metadata property should be rejected");
  }

  @Test
  public void shouldRejectUnimplementedGlobalFilterBinding() throws Exception {
    String json = "{"
        + "\"modelType\":\"dashboard\","
        + "\"name\":\"salesDashboard\","
        + "\"globalFilterBinding\":{\"source\":\"reactive-data-domain\"}"
        + "}";

    try {
      new ObjectMapper().readValue(json, BusinessModelMeta.class);
    } catch (JsonMappingException error) {
      assertTrue(error.getCause() instanceof IllegalArgumentException);
      assertTrue(error.getCause().getMessage().contains("globalFilterBinding"));
      return;
    }
    throw new AssertionError("unimplemented dashboard global filter binding should be rejected");
  }

  @Test
  public void shouldAcceptValidDashboardDefinition() {
    DashboardBusinessModel model = new DashboardBusinessModel(validMeta(), EMPTY_PROVIDER);

    assertTrue(model.getValidationResult().isValid());
  }

  @Test
  public void shouldProjectRuntimeUiAndSchemaFromFullBusinessModelName() {
    DashboardBusinessModelMeta meta = validMeta();
    meta.setNamespace("demo");
    meta.setName("salesDashboard");
    DashboardBusinessModel model = new DashboardBusinessModel(meta, EMPTY_PROVIDER);

    ModelBundle bundle = model.buildModelBundle();
    UIModel uiModel = bundle.getModel(UIModel.class, "/demo/sales-dashboard").orElse(null);
    SchemaNode schema = bundle.getModel(SchemaNode.class, "/demo/sales-dashboard").orElse(null);
    WebAPIModel webAPI = bundle.getModel(
        WebAPIModel.class, "GET /api/demo/sales-dashboard").orElse(null);
    Authority authority = bundle.getModel(Authority.class, "demo.salesDashboard").orElse(null);

    assertTrue(uiModel != null);
    assertEquals("/demo/sales-dashboard", uiModel.getPath());
    assertEquals("/demo/sales-dashboard", uiModel.getExtraProp(String.class, "schemaId").orElse(null));
    assertTrue(schema != null);
    assertEquals("page", schema.getType());
    assertEquals("/api/demo/sales-dashboard", schema.getProp(ApiConfig.class, "initApi")
        .map(ApiConfig::getUrl).orElse(null));
    SchemaNode dashboardSchema = schema.getProp(SchemaNode.class, "body").orElse(null);
    assertTrue(dashboardSchema != null);
    assertEquals("dashboard", dashboardSchema.getType());
    assertEquals(Boolean.FALSE, dashboardSchema.getProp(Boolean.class, "editable", null));
    assertTrue(webAPI != null);
    assertEquals("GET /api/demo/sales-dashboard", webAPI.getApi());
    assertEquals(HandlerType.REQUEST_MAPPING, webAPI.getHandlerType());
    assertEquals(
        "com.ouroboros.businessmodel.controller.DashboardStructureController",
        webAPI.getExtraProp(String.class, "className").orElse(null));
    assertEquals(
        "getDashboard(jakarta.servlet.http.HttpServletRequest)",
        webAPI.getExtraProp(String.class, "methodName").orElse(null));
    assertTrue(authority != null);
    assertEquals(Collections.singletonList("GET /api/demo/sales-dashboard"), authority.getApis());
    assertEquals(Collections.singletonList("/demo/sales-dashboard"), authority.getUiViews());
  }

  @Test
  public void shouldRejectUnsupportedWidgetType() {
    DashboardBusinessModelMeta meta = validMeta();
    meta.getWidgets().get(0).setWidgetName("unsupported-widget");

    ValidationResult result = new DashboardBusinessModel(meta, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    assertTrue(errorMessages(result).contains("dashboard.widgets[0].widgetName-invalid"));
  }

  @Test
  public void shouldRejectInvalidPosition() {
    DashboardBusinessModelMeta meta = validMeta();
    meta.getWidgets().get(0).getPosition().setX(Integer.valueOf(-1));

    ValidationResult result = new DashboardBusinessModel(meta, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    assertTrue(errorMessages(result).contains("dashboard.widgets[0].position.x-invalid"));
  }

  @Test
  public void shouldRejectWidgetOutsideCanvas() {
    DashboardBusinessModelMeta meta = validMeta();
    meta.getWidgets().get(0).getPosition().setX(Integer.valueOf(20));

    ValidationResult result = new DashboardBusinessModel(meta, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    assertTrue(errorMessages(result).contains("dashboard.widgets[0].bounds-invalid"));
  }

  @Test
  public void shouldRejectLayoutWithTooManyColumns() {
    DashboardBusinessModelMeta meta = validMeta();
    meta.getLayout().setCols(Integer.valueOf(25));

    ValidationResult result = new DashboardBusinessModel(meta, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    assertTrue(errorMessages(result).contains("dashboard.layout.cols-out-of-range"));
  }

  @Test
  public void shouldRejectOverlappingWidgets() {
    DashboardBusinessModelMeta meta = validMeta();
    DashboardBusinessModelMeta.WidgetDef second = widget();
    second.setId("secondChart");
    second.getPosition().setX(Integer.valueOf(6));
    meta.setWidgets(Arrays.asList(meta.getWidgets().get(0), second));

    ValidationResult result = new DashboardBusinessModel(meta, EMPTY_PROVIDER).getValidationResult();

    assertFalse(result.isValid());
    assertTrue(errorMessages(result).contains("dashboard.widgets[0].overlaps[1]"));
  }

  private DashboardBusinessModelMeta validMeta() {
    DashboardBusinessModelMeta meta = new DashboardBusinessModelMeta();
    meta.setName("salesDashboard");
    meta.setTitle("销售仪表板");
    meta.setWidgets(Collections.singletonList(widget()));
    return meta;
  }

  private DashboardBusinessModelMeta.WidgetDef widget() {
    DashboardBusinessModelMeta.WidgetDef widget = new DashboardBusinessModelMeta.WidgetDef();
    widget.setId("salesChart");
    widget.setWidgetName("chart");
    widget.setChartName("demo.salesChart");
    widget.getPosition().setX(Integer.valueOf(0));
    widget.getPosition().setY(Integer.valueOf(0));
    widget.getSize().setCols(Integer.valueOf(12));
    widget.getSize().setRows(Integer.valueOf(10));
    return widget;
  }

  private String errorMessages(ValidationResult result) {
    StringBuilder builder = new StringBuilder();
    for (Throwable error : result.getErrors()) {
      builder.append(error.getMessage()).append('\n');
    }
    return builder.toString();
  }
}
