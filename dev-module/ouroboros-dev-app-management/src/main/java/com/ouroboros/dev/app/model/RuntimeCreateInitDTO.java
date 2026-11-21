package com.ouroboros.dev.app.model;

import java.util.List;
import java.util.Map;

import com.ouroboros.web.SelectOption;

public class RuntimeCreateInitDTO {
  private List<SelectOption> availableBackends;
  private List<SelectOption> availableExposureStrategies;
  private String backendType;
  private String exposureStrategy;
  private String defaultBackendType;
  private String defaultExposureStrategy;
  private Map<String, Boolean> fieldVisibilityRules;
  private Map<String, Object> suggestedValues;
  private Map<String, Object> runtimeAccessPreview;
  private Map<String, Boolean> actionCapabilities;
  private String imageInputMode;
  private List<SelectOption> imageOptions;
  private Map<String, Integer> portBindings;
  private String image;
  private AppContainerRequest.ConfigFile configFile;

  public List<SelectOption> getAvailableBackends() {
    return availableBackends;
  }

  public void setAvailableBackends(List<SelectOption> availableBackends) {
    this.availableBackends = availableBackends;
  }

  public List<SelectOption> getAvailableExposureStrategies() {
    return availableExposureStrategies;
  }

  public void setAvailableExposureStrategies(List<SelectOption> availableExposureStrategies) {
    this.availableExposureStrategies = availableExposureStrategies;
  }

  public String getDefaultBackendType() {
    return defaultBackendType;
  }

  public String getBackendType() {
    return backendType;
  }

  public void setBackendType(String backendType) {
    this.backendType = backendType;
  }

  public String getExposureStrategy() {
    return exposureStrategy;
  }

  public void setExposureStrategy(String exposureStrategy) {
    this.exposureStrategy = exposureStrategy;
  }

  public void setDefaultBackendType(String defaultBackendType) {
    this.defaultBackendType = defaultBackendType;
  }

  public String getDefaultExposureStrategy() {
    return defaultExposureStrategy;
  }

  public void setDefaultExposureStrategy(String defaultExposureStrategy) {
    this.defaultExposureStrategy = defaultExposureStrategy;
  }

  public Map<String, Boolean> getFieldVisibilityRules() {
    return fieldVisibilityRules;
  }

  public void setFieldVisibilityRules(Map<String, Boolean> fieldVisibilityRules) {
    this.fieldVisibilityRules = fieldVisibilityRules;
  }

  public Map<String, Object> getSuggestedValues() {
    return suggestedValues;
  }

  public void setSuggestedValues(Map<String, Object> suggestedValues) {
    this.suggestedValues = suggestedValues;
  }

  public Map<String, Object> getRuntimeAccessPreview() {
    return runtimeAccessPreview;
  }

  public void setRuntimeAccessPreview(Map<String, Object> runtimeAccessPreview) {
    this.runtimeAccessPreview = runtimeAccessPreview;
  }

  public Map<String, Boolean> getActionCapabilities() {
    return actionCapabilities;
  }

  public void setActionCapabilities(Map<String, Boolean> actionCapabilities) {
    this.actionCapabilities = actionCapabilities;
  }

  public String getImageInputMode() {
    return imageInputMode;
  }

  public void setImageInputMode(String imageInputMode) {
    this.imageInputMode = imageInputMode;
  }

  public List<SelectOption> getImageOptions() {
    return imageOptions;
  }

  public void setImageOptions(List<SelectOption> imageOptions) {
    this.imageOptions = imageOptions;
  }

  public Map<String, Integer> getPortBindings() {
    return portBindings;
  }

  public void setPortBindings(Map<String, Integer> portBindings) {
    this.portBindings = portBindings;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public AppContainerRequest.ConfigFile getConfigFile() {
    return configFile;
  }

  public void setConfigFile(AppContainerRequest.ConfigFile configFile) {
    this.configFile = configFile;
  }
}
