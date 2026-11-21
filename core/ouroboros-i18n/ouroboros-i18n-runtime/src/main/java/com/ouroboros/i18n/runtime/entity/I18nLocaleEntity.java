package com.ouroboros.i18n.runtime.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * 国际化语言环境实体
 * 一条记录存储一个语言的完整字典
 *
 * @author AI Assistant
 */
@Model(rawName = "t_sys_i18n_locale", label = "多语言定义")
public class I18nLocaleEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 主键 (使用 locale 作为主键)
   */
  @Field
  @PrimaryKey
  private String locale;

  /**
   * 字典名称
   */
  @Field(nullable = true)
  private String name;

  /**
   * 版本号
   */
  @Field(nullable = true)
  private String version;

  /**
   * 是否启用
   */
  @Field(nullable = true)
  private Boolean enabled;

  /**
   * 是否默认语言
   */
  @Field(nullable = true)
  private Boolean isDefault;

  /**
   * 翻译消息 (JSON 格式)
   * 结构: {"menu": {"home": "首页"}, "button": {"save": "保存"}}
   */
  @Field(size = 65535)
  private String messages;

  /**
   * 文本方向 (LTR/RTL)
   */
  @Field(nullable = true)
  private String direction;

  /**
   * 创建时间
   */
  @Field(nullable = true)
  private LocalDateTime createdAt;

  /**
   * 创建者
   */
  @Field(nullable = true)
  private String createdBy;

  /**
   * 更新时间
   */
  @Field(nullable = true)
  private LocalDateTime updatedAt;

  /**
   * 更新者
   */
  @Field(nullable = true)
  private String updatedBy;

  public I18nLocaleEntity() {
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  public String getMessages() {
    return messages;
  }

  public void setMessages(String messages) {
    this.messages = messages;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }
}
