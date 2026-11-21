package com.ouroboros.businessmodel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.projection.ModelBundleProjector;
import com.ouroboros.businessmodel.controller.DashboardStructureController;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.businessmodel.validation.DashboardModelValidator;
import com.ouroboros.businessmodel.validation.ModelValidator;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;

public class DashboardBusinessModel extends AbstractBusinessModel<DashboardBusinessModelMeta, DashboardBusinessModel> {
  @Override
  public java.util.Optional<DashboardBusinessModelMeta> dashboard() {
    return java.util.Optional.of(meta);
  }

  public DashboardBusinessModel(DashboardBusinessModelMeta meta,
                                BusinessModelProvider businessModelProvider) {
    super(meta, businessModelProvider);
  }

  @Override
  protected ModelBundleProjector<DashboardBusinessModel> getProjector() {
    return DashboardBusinessModel::project;
  }

  private static ModelBundle project(DashboardBusinessModel model) {
    String path = BusinessModelUtil.buildKebabCasePath(model);
    String apiPath = "/api" + path;
    ModelBundle bundle = new ModelBundle();

    SimpleUIModel uiModel = new SimpleUIModel();
    uiModel.setTitle(model.getTitle());
    uiModel.setPath(path);
    uiModel.setType("amis");
    uiModel.setExtraProp("schemaId", path);
    bundle.putModel(UIModel.class, path, uiModel);
    bundle.putModel(SchemaNode.class, path, buildRuntimeSchema(model, path));

    WebAPIModel webAPIModel = SimpleWebAPIModel.builder()
        .get(apiPath)
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.REQUEST_MAPPING)
        .summary("读取 " + model.getTitle() + " 仪表板结构")
        .tag("business-model-dashboard")
        .extraProp("className", DashboardStructureController.class.getName())
        .extraProp("methodName", DashboardStructureController.HANDLER_METHOD_DESCRIPTOR)
        .build();
    bundle.putModel(WebAPIModel.class, webAPIModel.getApi(), webAPIModel);

    BusinessModelAuthority authority = new BusinessModelAuthority(model.getFullName(), model.getTitle());
    authority.addApi(webAPIModel.getApi());
    authority.addUiView(path);
    bundle.putModel(Authority.class, authority.getFullName(), authority);
    return bundle;
  }

  private static SchemaNode buildRuntimeSchema(DashboardBusinessModel model, String path) {
    SchemaNode page = new SchemaNode();
    page.setType("page");
    page.setProp("title", model.getTitle());
    ApiConfig initApi = new ApiConfig();
    initApi.setMethod("get");
    initApi.setUrl("/api" + path);
    page.setProp("initApi", initApi);

    SchemaNode dashboard = new SchemaNode();
    dashboard.setType("dashboard");
    dashboard.setProp("editable", Boolean.FALSE);
    dashboard.setProp("persistAsFields", Boolean.TRUE);
    dashboard.setProp("source", Collections.singletonList(buildChartWidgetDefinition()));
    page.setProp("body", dashboard);
    return page;
  }

  private static Map<String, Object> buildChartWidgetDefinition() {
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("name", "chart");
    definition.put("title", "图表");
    definition.put("allowHeader", Boolean.TRUE);
    definition.put("allowFooter", Boolean.FALSE);
    definition.put("sizeConstraints", Map.of(
        "min", Map.of("rows", 6, "cols", 6),
        "max", Map.of("rows", 24, "cols", 24)
    ));
    definition.put("defaultSize", Map.of("rows", 10, "cols", 12));
    definition.put("schemaTpl", Map.of(
        "type", "chart-runtime",
        "chartName", "#{chartName}"
    ));
    return definition;
  }

  @Override
  protected ModelValidator<DashboardBusinessModel> getValidator() {
    return new DashboardModelValidator();
  }

  @Override
  protected ValidationResult getRelationValidationResult(List<String> path) {
    return new ValidationResult(Collections.emptyList());
  }

  public DashboardBusinessModelMeta.LayoutConfig getLayout() {
    return meta.getLayout();
  }

  public List<DashboardBusinessModelMeta.WidgetDef> getWidgets() {
    return meta.getWidgets();
  }

  public static class Builder implements BusinessModelBuilder<DashboardBusinessModelMeta, DashboardBusinessModel> {
    @Override
    public Boolean support(BusinessModelMeta businessModelMeta) {
      return businessModelMeta instanceof DashboardBusinessModelMeta;
    }

    @Override
    public DashboardBusinessModel build(DashboardBusinessModelMeta businessModelMeta,
                                        BusinessModelProvider businessModelProvider) {
      return new DashboardBusinessModel(businessModelMeta, businessModelProvider);
    }
  }
}
