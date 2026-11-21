package com.ouroboros.dev.metadata;

import java.io.Serializable;
import java.util.Map;

/**
 * @author liansz
 **/
public class Metadata implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * 名称（唯一标识）
   */
  private String name;

  /**
   * 类型（元数据类型）
   */
  private String type;

  /**
   * 标签（中文）
   */
  private String label;

  private String sourceClassName;

  /**
   * 来源（元数据来源）
   */
  private MetadataSourceType sourceType;

  /**
   * 优先级
   */
  private int priority;

  /**
   * 详情（元数据详细信息）
   */
  private Map<String, Object> details;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getSourceClassName() {
    return sourceClassName;
  }

  public void setSourceClassName(String sourceClassName) {
    this.sourceClassName = sourceClassName;
  }

  public MetadataSourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(MetadataSourceType sourceType) {
    this.sourceType = sourceType;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public void setDetails(Map<String, Object> details) {
    this.details = details;
  }
}