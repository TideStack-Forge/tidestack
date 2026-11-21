package com.ouroboros.security.rbac.service.impl;

import java.util.Optional;
import java.util.List;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;

import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingContributor;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;

/**
 * Default identity-mapping adapter backed by the shared SSO binding service.
 *
 * <p>This is the only class that translates the public mapping port to
 * {@link UserSsoEntity}. Workflow and provider modules must depend on the
 * port, not on the entity or its service.
 *
 * <p>外部登录身份（{@code ssoUserIdentity}）只用于登录匹配；本端口解析的成员标识取自绑定的
 * 扩展属性。缺少成员标识时返回空，由调用方按"无映射"处理。
 */
@Service
public class UserSsoEnterpriseIdentityMappingService implements EnterpriseIdentityMappingService {

  private final UserSsoService userSsoService;
  private final List<EnterpriseIdentityMappingContributor> contributors;

  public UserSsoEnterpriseIdentityMappingService(UserSsoService userSsoService) {
    this(userSsoService, List.of());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public UserSsoEnterpriseIdentityMappingService(UserSsoService userSsoService,
      List<EnterpriseIdentityMappingContributor> contributors) {
    this.userSsoService = userSsoService;
    this.contributors = List.copyOf(contributors);
  }

  @Override
  public Optional<PlatformPrincipalRef> toPlatform(
      String connectionId, String externalMemberIdentity) {
    if (isBlank(connectionId) || isBlank(externalMemberIdentity)) {
      return Optional.empty();
    }
    var values = new LinkedHashSet<PlatformPrincipalRef>();
    userSsoService.findByChannelAndMemberId(connectionId, externalMemberIdentity)
        .map(UserSsoEntity::getUserIdentity)
        .filter(value -> !isBlank(value))
        .map(PlatformPrincipalRef::new).ifPresent(values::add);
    contributors.forEach(source -> source.toPlatform(connectionId, externalMemberIdentity).ifPresent(values::add));
    if (values.size() > 1) { throw new com.ouroboros.exception.ValidationException("企业成员的登录绑定和组织关联冲突，请管理员核对并解绑"); }
    return values.stream().findFirst();
  }

  @Override
  public Optional<String> toExternalMember(String connectionId, PlatformPrincipalRef principal) {
    if (isBlank(connectionId) || principal == null || isBlank(principal.userIdentity())) {
      return Optional.empty();
    }
    var matches = userSsoService.findByUserIdentity(principal.userIdentity()).stream()
        .filter(binding -> connectionId.equals(binding.getSsoChannel()))
        .map(binding -> memberIdOf(binding))
        .filter(value -> !isBlank(value))
        .distinct()
        .toList();
    if (matches.size() > 1) { return Optional.empty(); }
    var values = new LinkedHashSet<>(matches);
    contributors.forEach(source -> source.toExternalMember(connectionId, principal).ifPresent(values::add));
    if (values.size() > 1) { throw new com.ouroboros.exception.ValidationException("平台账号对应多个企业成员，请管理员核对并解绑"); }
    return values.stream().findFirst();
  }

  private String memberIdOf(UserSsoEntity binding) {
    Object value = binding.getExternalAttributes().get(ExternalIdentityAttributes.MEMBER_ID);
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
