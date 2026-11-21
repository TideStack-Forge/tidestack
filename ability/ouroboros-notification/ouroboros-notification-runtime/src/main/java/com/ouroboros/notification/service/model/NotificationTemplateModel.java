package com.ouroboros.notification.service.model;

import java.util.Map;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplate;

/**
 * @author liansz
 */
@Model(rawName = "t_notification_template", label = "通知模板")
@UniqueConstraint(fields = {"nameKey"}, nullsDistinct = true)
public class NotificationTemplateModel extends DefaultNotificationTemplate implements NotificationTemplate {

  /**
   * id
   */
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * 模板名称
   */
  @Field
  private String name;

  /** Case-insensitive name identity, independent of the database's text collation. */
  @Field(nullable = true, size = 64)
  private String nameKey;

  @Field(nullable = true)
  private Long revision;

  @Field(nullable = true)
  private String baseDefaultRevision;

  @Field(nullable = true)
  private String updateBy;

  @Field(nullable = true)
  private LocalDateTime updateAt;

  /**
   * 模板标题
   */
  @Field(nullable = true)
  private String title;

  /**
   * 载荷架构
   */
  @Field(nullable = true)
  private Map<String, Object> payloadSchema;

  /**
   * 渠道定义
   */
  @Field
  private Map<String, ?> channel;

  /**
   * 是否启用
   */
  @JsonProperty("isEnabled")
  @Field(type = "Boolean")
  private boolean isEnabled;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getNameKey() { return nameKey; }

  public void setNameKey(String nameKey) { this.nameKey = nameKey; }

  public Long getRevision() { return revision; }

  public void setRevision(Long revision) { this.revision = revision; }

  public String getBaseDefaultRevision() { return baseDefaultRevision; }

  public void setBaseDefaultRevision(String baseDefaultRevision) { this.baseDefaultRevision = baseDefaultRevision; }

  public String getUpdateBy() { return updateBy; }

  public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

  public LocalDateTime getUpdateAt() { return updateAt; }

  public void setUpdateAt(LocalDateTime updateAt) { this.updateAt = updateAt; }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public Map<String, Object> getPayloadSchema() {
    return payloadSchema;
  }

  @Override
  public void setPayloadSchema(Map<String, Object> payloadSchema) {
    this.payloadSchema = payloadSchema;
  }

  @Override
  public Map getChannel() {
    return channel;
  }

  @Override
  public void setChannel(Map<String, ?> channel) {
    this.channel = channel;
  }

  @Override
  @JsonProperty("isEnabled")
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }
}
