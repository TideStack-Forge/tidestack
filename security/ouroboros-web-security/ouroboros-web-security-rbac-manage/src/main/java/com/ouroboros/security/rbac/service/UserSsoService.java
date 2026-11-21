package com.ouroboros.security.rbac.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.security.rbac.entity.UserSsoEntity;

/**
 * SSO 绑定管理服务。
 *
 * <p>绑定唯一事实是 {@code (ssoChannel, ssoUserIdentity)}，语义为<b>外部登录身份</b>；审批等出站
 * 调用需要的企业内成员标识保存在扩展属性中，见 {@code ExternalIdentityAttributes}。
 */
public interface UserSsoService {

  Optional<UserSsoEntity> findByChannelAndExternalId(String ssoChannel, String ssoUserIdentity);

  /**
   * 按连接的成员标识反查绑定。入站方向（回调、任务快照）回传的是企业内成员标识，与登录身份
   * 作用域不同，必须走本入口而不是 {@link #findByChannelAndExternalId}。
   *
   * @param ssoChannel 连接标识
   * @param memberId   扩展属性中的成员标识
   * @return 恰好一条匹配时返回；无匹配返回空
   */
  Optional<UserSsoEntity> findByChannelAndMemberId(String ssoChannel, String memberId);

  List<UserSsoEntity> findByUserIdentity(String userIdentity);

  UserSsoEntity createBinding(String userIdentity, String ssoChannel, String ssoUserIdentity,
                               String displayName, String email, Map<String, Object> externalAttributes);

  /**
   * 覆盖绑定的扩展属性；成员标识等渠道属性发生刷新时使用。
   */
  boolean updateExternalAttributes(String id, Map<String, Object> externalAttributes);

  boolean deleteBinding(String id);

  boolean existsByChannelAndExternalId(String ssoChannel, String ssoUserIdentity);
}
