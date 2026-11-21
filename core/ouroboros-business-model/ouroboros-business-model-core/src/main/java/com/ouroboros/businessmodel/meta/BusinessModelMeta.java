package com.ouroboros.businessmodel.meta;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.ouroboros.businessmodel.json.BusinessModelMetaTypeIdResolver;

@SuppressWarnings("unused")
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "modelType")
@JsonTypeIdResolver(BusinessModelMetaTypeIdResolver.class)
public class BusinessModelMeta extends MetaWithExtraProps {
  public java.util.List<BusinessModelFieldMeta> fieldDefinitions() { return java.util.List.of(); }

  public java.util.List<BusinessModelRelationMeta> relationDefinitions() { return java.util.List.of(); }

  private String modelType = "default";
  private String source;
  private String formatVersion;
  private String name;
  private String namespace;
  private String title;
  private String description;
  private String icon;
  private Boolean isEnabled = false;
  private Boolean isAttachment = false;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getFormatVersion() {
    return formatVersion;
  }

  public void setFormatVersion(String formatVersion) {
    this.formatVersion = formatVersion;
  }

  public String getModelType() {
    return modelType;
  }

  public void setModelType(String modelType) {
    this.modelType = modelType;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFullName() {
    return StringUtils.isEmpty(getNamespace())
        ? getName()
        : (getNamespace() + "." + getName());
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean enabled) {
    isEnabled = enabled;
  }

  public Boolean getIsAttachment() {
    return isAttachment;
  }

  public void setIsAttachment(Boolean attachment) {
    isAttachment = attachment;
  }
}
