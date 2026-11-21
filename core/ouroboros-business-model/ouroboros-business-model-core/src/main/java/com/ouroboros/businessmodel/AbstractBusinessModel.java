package com.ouroboros.businessmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.projection.ModelBundleProjector;
import com.ouroboros.businessmodel.validation.ModelValidator;
import com.ouroboros.businessmodel.validation.ValidationResult;

public abstract class AbstractBusinessModel<M extends BusinessModelMeta, B extends AbstractBusinessModel<M, B>> implements BusinessModel {
  final protected M meta;
  final protected BusinessModelProvider businessModelProvider;
  private ValidationResult validationResult;

  public AbstractBusinessModel(M meta, BusinessModelProvider businessModelProvider) {
    this.meta = meta;
    this.businessModelProvider = businessModelProvider;
  }

  protected ValidationResult buildValidateResult() {
    return getValidator().validate((B) this);
  }

  protected abstract ModelBundleProjector<B> getProjector();

  protected abstract ModelValidator<B> getValidator();

  protected abstract ValidationResult getRelationValidationResult(List<String> path);

  @Override
  public ValidationResult getValidationResult() {
    if (validationResult == null) {
      validationResult = buildValidateResult();
    }
    return validationResult;
  }

  @Override
  public Boolean isValid() {
    return getValidationResult().isValid();
  }

  @Override
  public ValidationResult getRelationValidationResult() {
    return getRelationValidationResult(new ArrayList<>());
  }

  @Override
  public Boolean isRelationValid() {
    return getRelationValidationResult().isValid();
  }

  @Override
  public ModelBundle buildModelBundle() {
    return getProjector().project((B) this);
  }

  @Override
  public String getFormatVersion() {
    return meta.getFormatVersion();
  }

  @Override
  public String getName() {
    return meta.getName();
  }

  @Override
  public String getNamespace() {
    if (meta.getNamespace() == null) {
      return "";
    }
    return meta.getNamespace();
  }

  @Override
  public String getFullName() {
    return meta.getFullName();
  }

  @Override
  public String getTitle() {
    return meta.getTitle();
  }

  @Override
  public String getDescription() {
    return meta.getDescription();
  }

  @Override
  public String getIcon() {
    return meta.getIcon();
  }

  @Override
  public Boolean isEnabled() {
    return meta.getIsEnabled();
  }

  @Override
  public Boolean isAttachment() {
    return meta.getIsAttachment();
  }

  @Override
  public Map<String, Object> getExtraProps() {
    return meta.getExtraProps();
  }

  public M getMeta() {
    return meta;
  }

  @Override
  public Optional<Object> getExtraProp(String key) {
    return meta.getExtraProp(key);
  }
}
