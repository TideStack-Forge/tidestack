package com.ouroboros.businessmodel;

import java.util.List;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.projection.ModelBundleProjector;
import com.ouroboros.businessmodel.validation.CompositeViewModelValidator;
import com.ouroboros.businessmodel.validation.CompositeViewContractValidator;
import com.ouroboros.businessmodel.validation.ModelValidator;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.security.authority.Authority;

public class CompositeViewBusinessModel extends AbstractBusinessModel<CompositeViewBusinessModelMeta, CompositeViewBusinessModel> {
  @Override
  public java.util.Optional<CompositeViewBusinessModelMeta> compositeView() {
    return java.util.Optional.of(meta);
  }

  public CompositeViewBusinessModel(CompositeViewBusinessModelMeta meta,
                                    BusinessModelProvider businessModelProvider) {
    super(meta, businessModelProvider);
  }

  @Override
  protected ModelBundleProjector<CompositeViewBusinessModel> getProjector() {
    return CompositeViewBusinessModel::project;
  }

  private static ModelBundle project(CompositeViewBusinessModel model) {
    var errors = new java.util.ArrayList<>(model.getValidationResult().getErrors());
    errors.addAll(model.getRelationValidationResult().getErrors());
    if (!errors.isEmpty()) {
      throw new com.ouroboros.exception.BusinessException("复合业务视图投射校验失败: " + model.getFullName() + ": "
          + errors.stream().map(Throwable::getMessage).collect(java.util.stream.Collectors.joining("; ")));
    }
    ModelBundle bundle = new ModelBundle();
    String path = com.ouroboros.businessmodel.util.BusinessModelUtil.buildKebabCasePath(model);
    com.ouroboros.uimodel.SimpleUIModel ui = new com.ouroboros.uimodel.SimpleUIModel();
    ui.setPath(path);
    ui.setTitle(model.getTitle());
    ui.setType("amis");
    ui.setExtraProp("schemaId", path);
    bundle.putModel(com.ouroboros.uimodel.UIModel.class, path, ui);
    bundle.putModel(com.ouroboros.uischema.schema.SchemaNode.class, path,
        com.ouroboros.businessmodel.schema.CompositeViewRuntimeSchema.build(model.getMeta(), model.businessModelProvider));
    BusinessModelAuthority authority = new BusinessModelAuthority(model.getFullName(), model.getTitle());
    authority.addUiView(path);
    authority.addApi("POST /api/runtime/composite-view/{viewName:(?i)"
        + java.util.regex.Pattern.quote(model.getFullName()) + "}/execute");
    bundle.putModel(Authority.class, authority.getFullName(), authority);
    return bundle;
  }

  @Override
  protected ModelValidator<CompositeViewBusinessModel> getValidator() {
    return new CompositeViewModelValidator();
  }

  @Override
  protected ValidationResult getRelationValidationResult(List<String> path) {
    return new CompositeViewContractValidator(this).validate(meta);
  }

  public BusinessModelProvider getModelProvider() {
    return businessModelProvider;
  }

  public String getDatasetName() {
    return meta.getDatasetName();
  }

  public Object getEmbeddedQuery() {
    return meta.getEmbeddedQuery();
  }

  public List<CompositeViewBusinessModelMeta.ColumnDef> getColumns() {
    return meta.getColumns();
  }

  public CompositeViewBusinessModelMeta.FilterConfig getFilterConfig() {
    return meta.getFilterConfig();
  }

  public List<CompositeViewBusinessModelMeta.ActionButtonDef> getActionButtons() {
    return meta.getActionButtons();
  }

  public CompositeViewBusinessModelMeta.LayoutConfig getLayoutConfig() {
    return meta.getLayoutConfig();
  }

  public static class Builder implements BusinessModelBuilder<CompositeViewBusinessModelMeta, CompositeViewBusinessModel> {
    @Override
    public Boolean support(BusinessModelMeta businessModelMeta) {
      return businessModelMeta instanceof CompositeViewBusinessModelMeta;
    }

    @Override
    public CompositeViewBusinessModel build(CompositeViewBusinessModelMeta businessModelMeta,
                                            BusinessModelProvider businessModelProvider) {
      return new CompositeViewBusinessModel(businessModelMeta, businessModelProvider);
    }
  }
}
