package com.ouroboros.security.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import com.ouroboros.util.SPI;

/**
 * RoleCenter实现 - 聚合Spring Bean和SPI的RoleProvider
 * 
 * @author songmingxu
 */
@Component
public class RoleCenterImpl implements RoleCenter {

  private final List<RoleProvider> providers;

  @Autowired(required = false)
  public RoleCenterImpl(List<RoleProvider> springBeanProviders) {
    Proxy.INSTANCE = this;
    List<RoleProvider> allProviders = new ArrayList<>();

    // 添加Spring Bean providers
    if (springBeanProviders != null) {
      allProviders.addAll(springBeanProviders);
    }

    // 添加SPI providers
    SPI.getCachedServiceStream(RoleProvider.class).forEach(allProviders::add);

    // 按@Priority排序，高优先级在前
    allProviders.sort(AnnotationAwareOrderComparator.INSTANCE.reversed());

    this.providers = Collections.unmodifiableList(allProviders);
  }

  @Override
  public Optional<Role> getRole(String id) {
    return providers.stream()
        .map(provider -> provider.getRole(id))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @Override
  public List<Role> getRoles(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    return getAllRoles().stream()
        .filter(role -> ids.contains(role.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Role> getAllRoles() {
    return providers.stream()
        .flatMap(provider -> provider.getAllRole().values().stream())
        .collect(Collectors.toList());
  }

  public static class Proxy implements RoleCenter {
    private static RoleCenterImpl INSTANCE;

    @Override
    public Optional<Role> getRole(String id) {
      return INSTANCE.getRole(id);
    }

    @Override
    public List<Role> getRoles(Collection<String> ids) {
      return INSTANCE.getRoles(ids);
    }

    @Override
    public List<Role> getAllRoles() {
      return INSTANCE.getAllRoles();
    }
  }
}
