package com.ouroboros.modelmanage;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.vcs.VCSUserNameResolver;

@Component
public class DefaultVCSUserNameResolver {
  UserCenter userCenter;

  public DefaultVCSUserNameResolver(UserCenter userCenter) {
    this.userCenter = userCenter;
    Proxy.instance = this;
  }

  public String resolve(String userId) {
    if (StringUtils.isEmpty(userId)) {
      return null;
    }
    var user = userCenter.getUser(userId);
    if (!user.isPresent()) {
      return userId;
    }
    if (user.get() instanceof Employee employee) {
      return employee.getName();
    }
    return user.get().getUserIdentity();
  }

  public static class Proxy implements VCSUserNameResolver {
    private static DefaultVCSUserNameResolver instance;

    @Override
    public String resolve(String userId) {
      return instance.resolve(userId);
    }
  }
}
