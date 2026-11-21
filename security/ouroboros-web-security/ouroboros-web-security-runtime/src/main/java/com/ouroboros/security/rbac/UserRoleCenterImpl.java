package com.ouroboros.security.rbac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import com.ouroboros.util.SPI;

/**
 * UserRoleCenter实现 - 聚合Spring Bean和SPI的UserRoleProvider
 * 
 * @author songmingxu
 */
@Component
public class UserRoleCenterImpl implements UserRoleCenter {

  private final List<UserRoleProvider> providers;

  @Autowired(required = false)
  public UserRoleCenterImpl(List<UserRoleProvider> springBeanProviders) {
    Proxy.INSTANCE = this;
    List<UserRoleProvider> allProviders = new ArrayList<>();

    // 添加Spring Bean providers
    if (springBeanProviders != null) {
      allProviders.addAll(springBeanProviders);
    }

    // 添加SPI providers
    SPI.getCachedServiceStream(UserRoleProvider.class).forEach(allProviders::add);

    // 按@Priority排序，高优先级在前
    allProviders.sort(AnnotationAwareOrderComparator.INSTANCE.reversed());

    this.providers = Collections.unmodifiableList(allProviders);
  }

  @Override
  public List<Role> getRolesByUser(String userId) {
    return providers.stream()
        .flatMap(provider -> provider.getRolesByUser(userId).stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<User> getUsersByRole(String roleId) {
    return providers.stream()
        .flatMap(provider -> provider.getUsersByRole(roleId).stream())
        .collect(Collectors.toList());
  }

  public static class Proxy implements UserRoleCenter {
    private static UserRoleCenterImpl INSTANCE;

    @Override
    public List<Role> getRolesByUser(String userId) {
      return INSTANCE.getRolesByUser(userId);
    }

    @Override
    public List<User> getUsersByRole(String roleId) {
      return INSTANCE.getUsersByRole(roleId);
    }
  }
}
