package com.ouroboros.documentmanagement.infra.principal;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 用户主体类型
 *
 * <p>表示单个用户的主体类型，采用透传式解析逻辑。
 */
public class UserPrincipalType implements PrincipalType {
  private final PrincipalResolver resolver = new UserPrincipalResolver();
  private UserCenter userCenter;

  @Override
  public String getName() {
    return "USER";
  }

  @Override
  public String getDisplayName() {
    return "用户";
  }

  @Override
  public PrincipalResolver getResolver() {
    return resolver;
  }

  @Override
  public Optional<SchemaNode> getConfigUISchema() {
    FormItemNode node = new FormItemNode();
    node.setType("user-picker");
    node.setLabel("用户");
    return Optional.of(node);
  }

  /**
   * 用户 Principal 解析器（透传式）
   */
  private class UserPrincipalResolver implements PrincipalResolver {

    @Override
    public List<String> expandToUserIds(String principal) {
      if (principal == null || principal.isEmpty()) {
        return Collections.emptyList();
      }

      // 用户类型的 principal 就是用户ID，直接返回
      return Collections.singletonList(principal);
    }

    @Override
    public boolean containsUser(String principal, String userId) {
      if (principal == null || userId == null) {
        return false;
      }

      // 用户类型的 principal 直接与 userId 比较
      return principal.equals(userId);
    }

    @Override
    public List<String> getPrincipalsForUser(String userId) {
      if (userId == null || userId.isEmpty()) {
        return Collections.emptyList();
      }

      // 用户自身就是一个 Principal
      return Collections.singletonList(userId);
    }

    @Override
    public String getDisplayName(String principal) {
      if (userCenter == null) {
        userCenter = SpringContextUtil.getBean(UserCenter.class);
      }
      var user = userCenter.getUser(principal);
      return user.isPresent() ? user.get().getDisplayName() : principal;
    }
  }
}
