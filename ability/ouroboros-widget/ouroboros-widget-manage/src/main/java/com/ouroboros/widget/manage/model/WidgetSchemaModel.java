package com.ouroboros.widget.manage.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.util.Json;
import com.ouroboros.widget.GridSize;
import com.ouroboros.widget.WidgetDefinition;
import com.ouroboros.widget.WidgetSchema;

@Model(fullName = "WidgetSchemaModel", label = "小组件元数据", rawName = "sys_widget_schema")
public class WidgetSchemaModel {
  @Field(size = 20)
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field(size = 100)
  private String name;

  @Field(size = 100)
  private String title;

  @Field(size = 500, nullable = true)
  private String description;

  @Field(size = 100)
  private String generatorName;

  @Field(size = 100, nullable = true)
  private String category = "";

  @Field(size = 200, nullable = true)
  private String scene = "";

  @Field
  private Boolean allowHeader = true;

  @Field
  private Boolean allowFooter = false;

  @Field
  private Map<String, Object> sizeConstraints;

  @Field
  private Map<String, Object> defaultSize;

  @Field
  private Map<String, Object> config;

  @Field(size = 20, nullable = true)
  private String configDialogSize = "md";

  @Field
  private LocalDateTime createdAt;

  @Field
  private String createdBy;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private String updatedBy;

  public String getId() {
    return id;
  }

  public WidgetSchemaModel setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public WidgetSchemaModel setName(String name) {
    this.name = name;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public WidgetSchemaModel setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public WidgetSchemaModel setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getGeneratorName() {
    return generatorName;
  }

  public WidgetSchemaModel setGeneratorName(String generatorName) {
    this.generatorName = generatorName;
    return this;
  }

  public String getCategory() {
    return category;
  }

  public WidgetSchemaModel setCategory(String category) {
    this.category = category;
    return this;
  }

  public String getScene() {
    return scene;
  }

  public WidgetSchemaModel setScene(String scene) {
    this.scene = scene;
    return this;
  }

  public Boolean getAllowHeader() {
    return allowHeader;
  }

  public WidgetSchemaModel setAllowHeader(Boolean allowHeader) {
    this.allowHeader = allowHeader;
    return this;
  }

  public Boolean getAllowFooter() {
    return allowFooter;
  }

  public WidgetSchemaModel setAllowFooter(Boolean allowFooter) {
    this.allowFooter = allowFooter;
    return this;
  }

  public Map<String, Object> getSizeConstraints() {
    return sizeConstraints;
  }

  public WidgetSchemaModel setSizeConstraints(Map<String, Object> sizeConstraints) {
    this.sizeConstraints = sizeConstraints;
    return this;
  }

  public Map<String, Object> getDefaultSize() {
    return defaultSize;
  }

  public WidgetSchemaModel setDefaultSize(Map<String, Object> defaultSize) {
    this.defaultSize = defaultSize;
    return this;
  }

  public Map<String, Object> getConfig() {
    return config == null ? Collections.emptyMap() : config;
  }

  public WidgetSchemaModel setConfig(Map<String, Object> config) {
    this.config = config;
    return this;
  }

  public String getConfigDialogSize() {
    return configDialogSize;
  }

  public WidgetSchemaModel setConfigDialogSize(String configDialogSize) {
    this.configDialogSize = configDialogSize;
    return this;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public WidgetSchemaModel setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public WidgetSchemaModel setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public WidgetSchemaModel setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public WidgetSchemaModel setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
    return this;
  }

  public WidgetSchema toWidgetSchema() {
    return new WidgetSchema()
        .setGeneratorName(this.getGeneratorName())
        .setName(this.getName())
        .setTitle(this.getTitle())
        .setDescription(this.getDescription())
        .setCategory(this.getCategory())
        .setScene(this.getScene())
        .setAllowHeader(this.getAllowHeader())
        .setAllowFooter(this.getAllowFooter())
        .setSizeConstraints(Json.toBean(WidgetDefinition.SizeConstraints.class, Json.toJsonString(this.getSizeConstraints())))
        .setDefaultSize(Json.toBean(GridSize.class, Json.toJsonString(this.getDefaultSize())))
        .setConfig(this.getConfig())
        .setConfigDialogSize(this.getConfigDialogSize());
  }

  public static WidgetSchemaModel fromWidgetSchema(WidgetSchema widgetSchema) {
    return new WidgetSchemaModel()
        .setName(widgetSchema.getName())
        .setTitle(widgetSchema.getTitle())
        .setDescription(widgetSchema.getDescription())
        .setGeneratorName(widgetSchema.getGeneratorName())
        .setCategory(widgetSchema.getCategory())
        .setScene(widgetSchema.getScene())
        .setAllowHeader(widgetSchema.getAllowHeader())
        .setAllowFooter(widgetSchema.getAllowFooter())
        .setSizeConstraints(Json.toMap(Json.toJsonString(widgetSchema.getSizeConstraints())))
        .setDefaultSize(Json.toMap(Json.toJsonString(widgetSchema.getDefaultSize())))
        .setConfig(widgetSchema.getConfig())
        .setConfigDialogSize(widgetSchema.getConfigDialogSize());
  }
}
