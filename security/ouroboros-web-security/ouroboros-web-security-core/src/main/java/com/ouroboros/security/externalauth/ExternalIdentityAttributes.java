package com.ouroboros.security.externalauth;

/**
 * 外部身份绑定表扩展属性的公共键契约。
 *
 * <p>绑定唯一事实是 {@code (ssoChannel, ssoUserIdentity)}，即外部登录身份；它只负责登录匹配。
 * 外部审批 API 需要的是<b>企业内成员标识</b>，两者作用域不同（钉钉 unionId 与 userId、企微
 * open_userid 与 userid），因此不能共用同一个值。成员标识与渠道私有属性统一存放在绑定的扩展
 * 属性对象中，本类定义其中的公共键。
 *
 * <p>渠道私有属性使用带渠道前缀的键（例如 {@code dingtalk.deptId}、{@code wecom.userTicket}），
 * 与本类公共键不冲突。
 */
public final class ExternalIdentityAttributes {

  /**
   * 成员标识：在该连接下调用外部审批 API 所需的用户标识。
   *
   * <p>取值由各渠道的登录桥接在绑定阶段写入：钉钉为通讯录 {@code userId}、企业微信为企业内
   * {@code userid}、飞书为 {@code open_id}。取不到时该键缺失，调用方必须按"无映射"处理，
   * 不得回落到外部登录身份。
   */
  public static final String MEMBER_ID = "memberId";

  private ExternalIdentityAttributes() {
  }
}
