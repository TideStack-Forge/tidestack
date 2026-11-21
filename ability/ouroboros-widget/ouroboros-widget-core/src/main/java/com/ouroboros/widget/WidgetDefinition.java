package com.ouroboros.widget;

import java.util.List;
import java.util.Map;

import com.ouroboros.uischema.schema.SchemaNode;

/**
 * WidgetDefinition类用于定义一个组件的基本信息和配置
 * 包含场景、名称、标题、描述、分类等基本信息
 * 以及尺寸约束、默认配置、配置模式等高级配置
 */
public class WidgetDefinition {
  // 组件所属场景
  private String scene;
  // 组件名称
  private String name;
  // 组件标题
  private String title;
  // 组件描述
  private String description;
  // 组件分类
  private String category;
  // 是否允许头部
  private Boolean allowHeader;
  // 是否允许底部
  private Boolean allowFooter;
  // 尺寸约束
  private SizeConstraints sizeConstraints;
  // 默认尺寸
  private GridSize defaultSize;
  // 默认配置
  private WidgetConfig defaultConfig;
  // 默认样式配置
  private Map<String, Object> defaultStyleConfig;
  // 配置模式列表
  private List<SchemaNode> configFormItems;
  // 缩略图模式
  private SchemaNode thumbSchema;
  // 模式模板
  private Object schemaTpl;
  // 示例数据
  private Map<String, Object> exampleData;
  // 配置对话框大小
  private String configDialogSize;

  /**
   * 获取组件所属场景
   *
   * @return 场景名称
   */
  public String getScene() {
    return scene;
  }

  /**
   * 设置组件所属场景
   *
   * @param scene 场景名称
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setScene(String scene) {
    this.scene = scene;
    return this;
  }

  /**
   * 获取组件名称
   *
   * @return 组件名称
   */
  public String getName() {
    return name;
  }

  /**
   * 设置组件名称
   *
   * @param name 组件名称
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * 获取组件标题
   *
   * @return 组件标题
   */
  public String getTitle() {
    return title;
  }

  /**
   * 设置组件标题
   *
   * @param title 组件标题
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setTitle(String title) {
    this.title = title;
    return this;
  }

  /**
   * 获取组件描述
   *
   * @return 组件描述
   */
  public String getDescription() {
    return description;
  }

  /**
   * 设置组件描述
   *
   * @param description 组件描述
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * 获取组件分类
   *
   * @return 组件分类
   */
  public String getCategory() {
    return category;
  }

  /**
   * 设置组件分类
   *
   * @param category 组件分类
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setCategory(String category) {
    this.category = category;
    return this;
  }

  /**
   * 获取是否允许头部
   *
   * @return 是否允许头部
   */
  public Boolean getAllowHeader() {
    return allowHeader;
  }

  /**
   * 设置是否允许头部
   *
   * @param allowHeader 是否允许头部
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setAllowHeader(Boolean allowHeader) {
    this.allowHeader = allowHeader;
    return this;
  }

  /**
   * 获取是否允许底部
   *
   * @return 是否允许底部
   */
  public Boolean getAllowFooter() {
    return allowFooter;
  }

  /**
   * 设置是否允许底部
   *
   * @param allowFooter 是否允许底部
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setAllowFooter(Boolean allowFooter) {
    this.allowFooter = allowFooter;
    return this;
  }

  /**
   * 获取尺寸约束
   *
   * @return 尺寸约束对象
   */
  public SizeConstraints getSizeConstraints() {
    return sizeConstraints;
  }

  /**
   * 设置尺寸约束
   *
   * @param sizeConstraints 尺寸约束对象
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setSizeConstraints(SizeConstraints sizeConstraints) {
    this.sizeConstraints = sizeConstraints;
    return this;
  }

  public GridSize getDefaultSize() {
    return defaultSize;
  }

  public WidgetDefinition setDefaultSize(GridSize defaultSize) {
    this.defaultSize = defaultSize;
    return this;
  }

  /**
   * 获取默认配置
   *
   * @return 默认配置对象
   */
  public WidgetConfig getDefaultConfig() {
    return defaultConfig;
  }

  /**
   * 设置默认配置
   *
   * @param defaultConfig 默认配置对象
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setDefaultConfig(WidgetConfig defaultConfig) {
    this.defaultConfig = defaultConfig;
    return this;
  }

  public Map<String, Object> getDefaultStyleConfig() {
    return defaultStyleConfig;
  }

  public WidgetDefinition setDefaultStyleConfig(Map<String, Object> defaultStyleConfig) {
    this.defaultStyleConfig = defaultStyleConfig;
    return this;
  }

  /**
   * 获取配置表单项列表
   * 该方法用于返回配置表单项的列表，通常用于获取界面配置相关的数据结构
   *
   * @return 返回一个SchemaNode类型的列表，包含所有配置表单项信息
   */
  public List<SchemaNode> getConfigFormItems() {
    return configFormItems;
  }

  /**
   * 设置组件配置表单项的方法
   *
   * @param configFormItems 配置表单项的列表，每个表项都是一个SchemaNode对象
   * @return 返回当前WidgetDefinition对象，支持链式调用
   */
  public WidgetDefinition setConfigFormItems(List<SchemaNode> configFormItems) {
    // 将传入的配置表单项列表赋值给当前对象的configFormItems属性
    this.configFormItems = configFormItems;
    // 返回当前对象实例，以便支持链式调用
    return this;
  }

  /**
   * 获取缩略图模式
   *
   * @return 缩略图模式对象
   */
  public SchemaNode getThumbSchema() {
    return thumbSchema;
  }

  /**
   * 设置缩略图模式
   *
   * @param thumbSchema 缩略图模式对象
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setThumbSchema(SchemaNode thumbSchema) {
    this.thumbSchema = thumbSchema;
    return this;
  }

  /**
   * 获取模式模板
   *
   * @return 模式模板对象
   */
  public Object getSchemaTpl() {
    return schemaTpl;
  }

  /**
   * 设置模式模板
   *
   * @param schemaTpl 模式模板对象
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setSchemaTpl(Object schemaTpl) {
    this.schemaTpl = schemaTpl;
    return this;
  }

  public Map<String, Object> getExampleData() {
    return exampleData;
  }

  public WidgetDefinition setExampleData(Map<String, Object> exampleData) {
    this.exampleData = exampleData;
    return this;
  }

  /**
   * 获取配置对话框大小
   *
   * @return 配置对话框大小 (xs, sm, md, lg, xl, full)
   */
  public String getConfigDialogSize() {
    return configDialogSize;
  }

  /**
   * 设置配置对话框大小
   *
   * @param configDialogSize 配置对话框大小 (xs, sm, md, lg, xl, full)，默认为 md
   * @return 当前对象实例，支持链式调用
   */
  public WidgetDefinition setConfigDialogSize(String configDialogSize) {
    this.configDialogSize = configDialogSize;
    return this;
  }

  /**
   * SizeConstraints内部类，用于定义组件的尺寸约束
   * 包含最小尺寸、最大尺寸和默认尺寸
   */
  public static class SizeConstraints {
    // 最小尺寸
    private GridSize min;
    // 最大尺寸
    private GridSize max;

    /**
     * 获取最小尺寸
     *
     * @return 最小尺寸对象
     */
    public GridSize getMin() {
      return min;
    }

    /**
     * 设置最小尺寸
     *
     * @param min 最小尺寸对象
     * @return 当前对象实例，支持链式调用
     */
    public SizeConstraints setMin(GridSize min) {
      this.min = min;
      return this;
    }

    /**
     * 获取最大尺寸
     *
     * @return 最大尺寸对象
     */
    public GridSize getMax() {
      return max;
    }

    /**
     * 设置最大尺寸
     *
     * @param max 最大尺寸对象
     * @return 当前对象实例，支持链式调用
     */
    public SizeConstraints setMax(GridSize max) {
      this.max = max;
      return this;
    }
  }

}
