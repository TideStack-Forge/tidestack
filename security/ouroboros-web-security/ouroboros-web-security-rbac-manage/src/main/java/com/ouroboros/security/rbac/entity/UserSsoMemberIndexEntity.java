package com.ouroboros.security.rbac.entity;

import java.io.Serializable;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.UniqueConstraint;

/**
 * 外部身份成员 ID 索引。
 *
 * <p>{@link UserSsoEntity#getExternalAttributes()} 是渠道自定义属性的唯一事实源，但 Map 列无法
 * 建立等值查询索引；入站方向（回调、任务快照）需要按企业内成员标识反查平台用户。本表是扩展属性中
 * 成员标识（{@code ExternalIdentityAttributes.MEMBER_ID}）的索引投影，由 {@code UserSsoService}
 * 与绑定同事务维护，不对外暴露。
 *
 * <p>该索引固定只有一个键，不会随渠道新增属性而扩张；新增需要检索的属性时再评估是否需要同类索引，
 * 扩展属性本身不需要改绑定表结构。
 */
@Model(
    rawName = "t_sys_user_sso_member_index",
    label = "用户SSO成员ID索引"
)
@UniqueConstraint(name = "uk_sso_member_channel_member", fields = {"ssoChannel", "memberId"})
public class UserSsoMemberIndexEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /** 归属的 {@link UserSsoEntity#getId()}。 */
  @Field
  private String bindingId;

  @Field
  private String ssoChannel;

  @Field
  private String memberId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBindingId() {
    return bindingId;
  }

  public void setBindingId(String bindingId) {
    this.bindingId = bindingId;
  }

  public String getSsoChannel() {
    return ssoChannel;
  }

  public void setSsoChannel(String ssoChannel) {
    this.ssoChannel = ssoChannel;
  }

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(String memberId) {
    this.memberId = memberId;
  }
}
