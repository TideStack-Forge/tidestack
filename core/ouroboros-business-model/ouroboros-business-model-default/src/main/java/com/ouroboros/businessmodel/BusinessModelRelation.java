package com.ouroboros.businessmodel;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;

@SuppressWarnings("unused")
public class BusinessModelRelation implements BusinessRelationView {
  private final DefaultBusinessModel model;
  private final BusinessModelRelationMeta meta;
  private final BusinessModelProvider businessModelProvider;
  private DefaultBusinessModel relatedModel;

  public BusinessModelRelation(DefaultBusinessModel model, BusinessModelRelationMeta meta, BusinessModelProvider businessModelProvider) {
    this.model = model;
    this.meta = meta;
    this.businessModelProvider = businessModelProvider;
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

  public String getRelatedModelName() {
    return meta.getModel();
  }

  public DefaultBusinessModel getRelatedModel() {
    if (relatedModel == null) {
      var relatedModelName = getRelatedModelName();
      relatedModel = businessModelProvider.getBusinessModel(relatedModelName)
          .filter(DefaultBusinessModel.class::isInstance)
          .map(DefaultBusinessModel.class::cast)
          .orElse(null);
    }
    return relatedModel;
  }

  public String getType() {
    return meta.getType();
  }

  public Boolean getIsRequired() {
    return meta.getIsRequired();
  }

  public Boolean getSubmitTogether() {
    return Objects.equals(getType(), "child") && Optional.ofNullable(meta.getSubmitTogether()).orElse(false);
  }

  public String getReferenceKey() {
    return meta.getReferenceKey();
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return meta.getExtraProps();
  }

  public <T> Optional<T> getExtraProp(Class<T> type, String key) {
    return meta.getExtraProp(type, key);
  }

  public Optional<Object> getExtraProp(String key) {
    return meta.getExtraProp(key);
  }

  public <T> T getExtraProp(Class<T> type, String key, T defaultValue) {
    return meta.getExtraProps(type, key, defaultValue);
  }

  public <T> Optional<T> getExtraPropDeep(Class<T> type, String... pathKeys) {
    return meta.getExtraPropDeep(type, pathKeys);
  }

  @JsonIgnore
  public DefaultBusinessModel getModel() {
    return model;
  }

  @JsonIgnore
  public String getKey() {
    if ("child".equals(getType())) {
      return meta.getExtraProp(String.class, "key")
          .orElseGet(() -> getRelatedModel().getRelation(getReferenceKey())
              .map(BusinessModelRelation::getReferenceKey)
              .orElse("id"));
    }
    return getName();
  }
}
