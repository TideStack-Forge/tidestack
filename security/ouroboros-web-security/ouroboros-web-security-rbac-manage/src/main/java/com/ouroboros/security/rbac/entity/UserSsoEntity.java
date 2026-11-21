package com.ouroboros.security.rbac.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

@Model(
    rawName = "t_sys_user_sso",
    label = "用户SSO绑定表"
)
@UniqueConstraint(fields = {"ssoChannel", "ssoUserIdentity"})
public class UserSsoEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String userIdentity;

  @Field
  private String ssoChannel;

  @Field
  private String ssoUserIdentity;

  @Field(nullable = true)
  private LocalDateTime createdAt;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private String externalDisplayName;

  @Field(nullable = true)
  private String externalEmail;

  /**
   * 渠道自定义属性。公共键为成员标识（见 {@code ExternalIdentityAttributes.MEMBER_ID}），
   * 渠道私有键带渠道前缀。外部登录身份仍保存在 {@code ssoUserIdentity}，两者作用域不同。
   */
  @Field(nullable = true)
  private Map<String, Object> externalAttributes;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserIdentity() {
    return userIdentity;
  }

  public void setUserIdentity(String userIdentity) {
    this.userIdentity = userIdentity;
  }

  public String getSsoChannel() {
    return ssoChannel;
  }

  public void setSsoChannel(String ssoChannel) {
    this.ssoChannel = ssoChannel;
  }

  public String getSsoUserIdentity() {
    return ssoUserIdentity;
  }

  public void setSsoUserIdentity(String ssoUserIdentity) {
    this.ssoUserIdentity = ssoUserIdentity;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getExternalDisplayName() {
    return externalDisplayName;
  }

  public void setExternalDisplayName(String externalDisplayName) {
    this.externalDisplayName = externalDisplayName;
  }

  public String getExternalEmail() {
    return externalEmail;
  }

  public void setExternalEmail(String externalEmail) {
    this.externalEmail = externalEmail;
  }

  /** 扩展属性视图；未设置时返回空对象，调用方无需判空。 */
  public Map<String, Object> getExternalAttributes() {
    return externalAttributes == null ? Collections.emptyMap() : externalAttributes;
  }

  public void setExternalAttributes(Map<String, Object> externalAttributes) {
    this.externalAttributes = externalAttributes;
  }
}
