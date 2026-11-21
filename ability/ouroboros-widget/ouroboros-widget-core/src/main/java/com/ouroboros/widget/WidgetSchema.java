package com.ouroboros.widget;

import java.util.Collections;
import java.util.Map;

public class WidgetSchema {
  private String generatorName;
  private String name;
  private String title;
  private String description;
  private String category;
  private String scene;
  private Boolean allowHeader = true;
  private Boolean allowFooter = true;
  private WidgetDefinition.SizeConstraints sizeConstraints;
  private GridSize defaultSize;
  private Map<String, Object> config;
  private String configDialogSize;

  public String getGeneratorName() {
    return generatorName;
  }

  public WidgetSchema setGeneratorName(String generatorName) {
    this.generatorName = generatorName;
    return this;
  }

  public String getName() {
    return name;
  }

  public WidgetSchema setName(String name) {
    this.name = name;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public WidgetSchema setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public WidgetSchema setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getCategory() {
    return category;
  }

  public WidgetSchema setCategory(String category) {
    this.category = category;
    return this;
  }

  public String getScene() {
    return scene;
  }

  public WidgetSchema setScene(String scene) {
    this.scene = scene;
    return this;
  }

  public Boolean getAllowHeader() {
    return allowHeader;
  }

  public WidgetSchema setAllowHeader(Boolean allowHeader) {
    this.allowHeader = allowHeader;
    return this;
  }

  public Boolean getAllowFooter() {
    return allowFooter;
  }

  public WidgetSchema setAllowFooter(Boolean allowFooter) {
    this.allowFooter = allowFooter;
    return this;
  }

  public WidgetDefinition.SizeConstraints getSizeConstraints() {
    return sizeConstraints;
  }

  public WidgetSchema setSizeConstraints(WidgetDefinition.SizeConstraints sizeConstraints) {
    this.sizeConstraints = sizeConstraints;
    return this;
  }

  public GridSize getDefaultSize() {
    return defaultSize;
  }

  public WidgetSchema setDefaultSize(GridSize defaultSize) {
    this.defaultSize = defaultSize;
    return this;
  }

  public Map<String, Object> getConfig() {
    return config == null ? Collections.emptyMap() : config;
  }

  public WidgetSchema setConfig(Map<String, Object> config) {
    this.config = config;
    return this;
  }

  public String getConfigDialogSize() {
    return configDialogSize;
  }

  public WidgetSchema setConfigDialogSize(String configDialogSize) {
    this.configDialogSize = configDialogSize;
    return this;
  }
}
