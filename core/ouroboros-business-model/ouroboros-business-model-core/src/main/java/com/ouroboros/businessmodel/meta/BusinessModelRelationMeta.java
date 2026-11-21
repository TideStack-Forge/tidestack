package com.ouroboros.businessmodel.meta;

import java.util.LinkedHashMap;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.ouroboros.util.Maps;

public class BusinessModelRelationMeta extends MetaWithExtraProps implements Cloneable {
  private String name;
  private String title;
  private String description;
  private String model;
  private String type;
  private Boolean submitTogether = false;
  private Boolean isRequired = false;
  private String referenceKey;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getType() {
    if ("oneToMany".equalsIgnoreCase(type)) {
      return "child";
    }
    if ("manyToOne".equalsIgnoreCase(type)) {
      return "master";
    }
    if ("oneToOne".equalsIgnoreCase(type)) {
      return "reference";
    }
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean getIsRequired() {
    return Optional.ofNullable(isRequired).orElse(false);
  }

  public void setIsRequired(Boolean isRequired) {
    this.isRequired = isRequired;
  }

  public Boolean getSubmitTogether() {
    return Optional.ofNullable(submitTogether).orElse(false);
  }

  public void setSubmitTogether(Boolean submitTogether) {
    this.submitTogether = submitTogether;
  }

  public String getReferenceKey() {
    return referenceKey;
  }

  public void setReferenceKey(String referenceKey) {
    this.referenceKey = referenceKey;
  }

  @Override
  public BusinessModelRelationMeta clone() {
    try {
      var cloned = (BusinessModelRelationMeta) super.clone();
      cloned.extraProps = Maps.deepClone(extraProps, LinkedHashMap::new);
      return cloned;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}
