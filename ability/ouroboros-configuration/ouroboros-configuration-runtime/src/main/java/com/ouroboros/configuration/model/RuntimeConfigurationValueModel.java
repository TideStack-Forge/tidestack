package com.ouroboros.configuration.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * @author liansz
 */
@Model(rawName = "t_sys_configuration_value", label = "统一配置值存储表")
public class RuntimeConfigurationValueModel implements ConfigurationValueModel, Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey
  private String key;

  /** Structured settings can exceed the data model's default 255-character string. */
  @Field(size = 1024, extraProps = {"allowVarcharToText", "true"})
  private String value;

  /** Monotonic revision for persisted values; nullable for rows created before CAS support. */
  @Field(nullable = true)
  private Long revision;

  /** Stable for a persisted row; new inserts receive a new identity after restoration. */
  @Field(nullable = true, size = 36)
  private String generation;

  @Field
  private LocalDateTime updateAt;

  @Field
  private String updateBy;

  @Override
  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  @Override
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Long getRevision() {
    return revision;
  }

  public String getGeneration() { return generation; }

  public void setGeneration(String generation) { this.generation = generation; }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public LocalDateTime getUpdateAt() {
    return updateAt;
  }

  public void setUpdateAt(LocalDateTime updateAt) {
    this.updateAt = updateAt;
  }

  public String getUpdateBy() {
    return updateBy;
  }

  public void setUpdateBy(String updateBy) {
    this.updateBy = updateBy;
  }
}
