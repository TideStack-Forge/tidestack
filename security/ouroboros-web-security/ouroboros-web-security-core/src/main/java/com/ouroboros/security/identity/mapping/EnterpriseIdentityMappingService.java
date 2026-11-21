package com.ouroboros.security.identity.mapping;

import java.util.Optional;

/**
 * Public port for connection-scoped external identity mappings.
 *
 * <p>The default implementation reads the shared SSO binding service, but a
 * provider may supply another implementation (for example, a directory or a
 * provider-specific mapping store) without making workflow code depend on a
 * persistence entity. The connection ID is the complete scope; provider
 * identity is derived from the connection catalogue.
 *
 * <p>出站方向解析的是<b>企业内成员标识</b>（调用外部审批 API 所需的用户标识），不是外部登录
 * 身份。两者作用域不同（钉钉 unionId 与 userId、企微 open_userid 与 userid），因此本端口
 * 不得用登录身份兜底。
 */
public interface EnterpriseIdentityMappingService {

  /**
   * Resolve an external identity to a platform principal.
   *
   * @param connectionId         connection scope
   * @param externalMemberIdentity 企业内成员标识（回调/任务快照中回传的值）
   * @return the platform principal when exactly one mapping exists
   */
  Optional<PlatformPrincipalRef> toPlatform(String connectionId, String externalMemberIdentity);

  /**
   * Resolve a platform principal to the enterprise member identity of the connection.
   *
   * @param connectionId connection scope
   * @param principal    platform principal
   * @return the member identity when exactly one mapping exists
   */
  Optional<String> toExternalMember(String connectionId, PlatformPrincipalRef principal);
}
