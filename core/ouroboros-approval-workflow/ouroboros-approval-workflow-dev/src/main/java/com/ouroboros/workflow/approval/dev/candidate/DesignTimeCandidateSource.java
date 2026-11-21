package com.ouroboros.workflow.approval.dev.candidate;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

public class DesignTimeCandidateSource {
  private String name;
  private String label;
  private CandidateProvider.ConfigMode configMode;
  private SchemaNode schema;
  private String valueKey;
  private List<String> disabledIn = new ArrayList<>();
  private Integer max;
  private Integer min;
  private List<CandidateProvider.CandidateItem> items = new ArrayList<>();

  public static DesignTimeCandidateSource fromProvider(String appName, ApprovalWorkflowDesignTimeCandidateProvider provider) {
    DesignTimeCandidateSource source = new DesignTimeCandidateSource();
    source.setName(provider.getName());
    source.setLabel(provider.getLabel());
    source.setConfigMode(provider.getConfigMode());
    source.setSchema(provider.getSchema());
    source.setValueKey(provider.getValueKey());
    source.setDisabledIn(provider.getDisabledIn());
    source.setMax(provider.getMax());
    source.setMin(provider.getMin());
    source.setItems(provider.getItems(appName));
    return source;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public CandidateProvider.ConfigMode getConfigMode() {
    return configMode;
  }

  public void setConfigMode(CandidateProvider.ConfigMode configMode) {
    this.configMode = configMode;
  }

  public SchemaNode getSchema() {
    return schema;
  }

  public void setSchema(SchemaNode schema) {
    this.schema = schema;
  }

  public String getValueKey() {
    return valueKey;
  }

  public void setValueKey(String valueKey) {
    this.valueKey = valueKey;
  }

  public List<String> getDisabledIn() {
    return disabledIn;
  }

  public void setDisabledIn(List<String> disabledIn) {
    this.disabledIn = disabledIn == null ? new ArrayList<>() : new ArrayList<>(disabledIn);
  }

  public Integer getMax() {
    return max;
  }

  public void setMax(Integer max) {
    this.max = max;
  }

  public Integer getMin() {
    return min;
  }

  public void setMin(Integer min) {
    this.min = min;
  }

  public List<CandidateProvider.CandidateItem> getItems() {
    return items;
  }

  public void setItems(List<CandidateProvider.CandidateItem> items) {
    this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
  }
}
