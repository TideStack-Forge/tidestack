package com.ouroboros.security.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.SPI;

/**
 * 用户中心实现
 * 
 * @author liansz
 */
@Component
public class UserCenterImpl implements UserCenter {

  private final List<UserProvider> allProviders;

  public UserCenterImpl(@Autowired(required = false) List<UserProvider> springBeanProviders) {
    Proxy.INSTANCE = this;
    
    List<UserProvider> providers = new ArrayList<>();
    
    // 1. 添加Spring Bean Provider
    if (springBeanProviders != null) {
      providers.addAll(springBeanProviders);
    }
    
    // 2. 添加SPI Provider
    providers.addAll(SPI.getSortedServiceStream(UserProvider.class)
        .collect(Collectors.toList()));
    
    // 3. 按@Priority排序（数值越大优先级越高）
    this.allProviders = providers.stream()
        .sorted(Comparator.comparing(this::getPriority).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public boolean publicSubjectsReady() {
    return !allProviders.isEmpty() && allProviders.stream().allMatch(UserProvider::publicSubjectsReady);
  }

  @Override
  public Optional<User> getUser(String id) {
    return allProviders
        .stream()
        .map(provider -> provider.getUser(id))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(User.class::cast)
        .findFirst();
  }

  @Override
  public List<User> getUserCandidates(String id) {
    return allProviders.stream().map(provider -> provider.getUser(id))
        .flatMap(Optional::stream).map(User.class::cast).toList();
  }

  @Override
  public List<User> getUsers(Collection<String> userIdentities) {
    return allProviders
        .stream()
        .flatMap(provider -> provider.getAllUser().stream())
        .filter(user -> userIdentities.contains(user.getUserIdentity()))
        .collect(Collectors.toList());
  }

  @Override
  public List<User> getAllUsers() {
    return allProviders
        .stream()
        .flatMap(provider -> provider.getAllUser().stream())
        .collect(Collectors.toList());
  }

  /**
   * 获取Provider优先级
   */
  private int getPriority(UserProvider provider) {
    Priority priority = provider.getClass().getAnnotation(Priority.class);
    return priority != null ? priority.value() : 0;
  }

  public static class Proxy implements UserCenter {
    private static UserCenterImpl INSTANCE;

    @Override
    public boolean publicSubjectsReady() { return INSTANCE != null && INSTANCE.publicSubjectsReady(); }

    @Override
    public Optional<User> getUser(String userIdentity) {
      return INSTANCE.getUser(userIdentity);
    }

    @Override
    public List<User> getUserCandidates(String id) { return INSTANCE.getUserCandidates(id); }

    @Override
    public List<User> getUsers(Collection<String> userIdentities) {
      return INSTANCE.getUsers(userIdentities);
    }

    @Override
    public List<User> getAllUsers() {
      return INSTANCE.getAllUsers();
    }
  }
}
