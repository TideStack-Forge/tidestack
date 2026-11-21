package com.ouroboros.businessmodel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;

@SuppressWarnings("unused")
public class BusinessModelField implements BusinessFieldView {
  private final DefaultBusinessModel model;
  private final BusinessModelFieldMeta meta;

  public BusinessModelField(DefaultBusinessModel model, BusinessModelFieldMeta meta) {
    this.model = model;
    this.meta = meta;
  }

  public String getName() {
    return meta.getName();
  }

  public String getTitle() {
    return meta.getTitle();
  }

  public String getDescription() {
    return meta.getDescription();
  }

  public String getType() {
    return meta.getType();
  }

  public String getDefaultValue() {
    return meta.getDefaultValue();
  }

  public Boolean getIsRequired() {
    return meta.getIsRequired();
  }

  public Boolean getIsPrimaryKey() {
    return meta.getIsPrimaryKey();
  }

  public Boolean getIsDisplayField() {
    return meta.getIsDisplayField();
  }

  public Boolean getIsUnique() {
    return meta.getIsUnique();
  }

  public List<String> getValidators() {
    return meta.getValidators();
  }

  public Map<String, Object> getCustomInput() {
    return meta.getCustomInput() == null ? null : Collections.unmodifiableMap(meta.getCustomInput());
  }

  public Map<String, Object> getCustomDisplay() {
    return meta.getCustomDisplay() == null ? null : Collections.unmodifiableMap(meta.getCustomDisplay());
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return meta.getExtraProps();
  }

  public Optional<Object> getExtraProp(String key) {
    return meta.getExtraProp(key);
  }

  public <T> Optional<T> getExtraProp(Class<T> type, String key) {
    return meta.getExtraProp(type, key);
  }

  public BusinessModel getModel() {
    return model;
  }
}
