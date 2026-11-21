package com.ouroboros.businesslog.service.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;

/**
 * 合并后的业务日志模板视图。
 */
public class BusinessOperationTemplateSourceView extends DefaultBusinessOperationTemplate {

  private String id;

  private String templateKey;

  private BusinessOperationTemplateSource source;

  private List<BusinessOperationTemplateSource> availableSources = Collections.emptyList();

  private List<BusinessOperationTemplateSource> overridesSources = Collections.emptyList();

  @JsonProperty("isControlled")
  private boolean isControlled;

  public static BusinessOperationTemplateSourceView fromTemplate(String templateKey,
                                                                 BusinessOperationTemplateSource source,
                                                                 BusinessOperationTemplate template) {
    BusinessOperationTemplateSourceView view = new BusinessOperationTemplateSourceView();
    view.setTemplateKey(templateKey);
    view.setSource(source);
    view.setControlled(source == BusinessOperationTemplateSource.DATABASE);
    view.setHttpMethod(template.getHttpMethod());
    view.setUrl(template.getUrl());
    view.setModuleName(template.getModuleName());
    view.setOperation(template.getOperation());
    view.setLevel(template.getLevel());
    view.setBusinessKeyName(template.getBusinessKeyName());
    view.setSummaryTemplate(template.getSummaryTemplate());
    view.setSensitiveFields(template.getSensitiveFields() == null
        ? Collections.emptyList()
        : new ArrayList<>(template.getSensitiveFields()));
    view.setShouldSaveResponseBody(template.isShouldSaveResponseBody());

    if (template instanceof DefaultBusinessOperationTemplate) {
      view.setName(((DefaultBusinessOperationTemplate) template).getName());
    }

    if (template instanceof BusinessOperationTemplateModel) {
      view.setId(((BusinessOperationTemplateModel) template).getId());
    }

    return view;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public void setTemplateKey(String templateKey) {
    this.templateKey = templateKey;
  }

  public BusinessOperationTemplateSource getSource() {
    return source;
  }

  public void setSource(BusinessOperationTemplateSource source) {
    this.source = source;
  }

  public List<BusinessOperationTemplateSource> getAvailableSources() {
    return availableSources;
  }

  public void setAvailableSources(List<BusinessOperationTemplateSource> availableSources) {
    this.availableSources = availableSources == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(availableSources));
  }

  public List<BusinessOperationTemplateSource> getOverridesSources() {
    return overridesSources;
  }

  public void setOverridesSources(List<BusinessOperationTemplateSource> overridesSources) {
    this.overridesSources = overridesSources == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(overridesSources));
  }

  @JsonProperty("isControlled")
  public boolean isControlled() {
    return isControlled;
  }

  public String getSourceLabel() {
    return source == null ? null : source.getLabel();
  }

  public String getAvailableSourcesDisplay() {
    return joinLabels(availableSources);
  }

  public String getOverridesSourcesDisplay() {
    return joinLabels(overridesSources);
  }

  public void setControlled(boolean controlled) {
    isControlled = controlled;
  }

  private String joinLabels(List<BusinessOperationTemplateSource> sources) {
    if (sources == null || sources.isEmpty()) {
      return "";
    }
    return sources.stream()
        .map(BusinessOperationTemplateSource::getLabel)
        .reduce((left, right) -> left + " > " + right)
        .orElse("");
  }
}
