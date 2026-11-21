package com.ouroboros.security.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.SPI;

/**
 * 用户-主体绑定关系中心实现
 * 
 * @author songmingxu
 */
@Component
public class UserSubjectCenterImpl implements UserSubjectCenter {

  private final List<UserSubjectProvider> allProviders;

  public UserSubjectCenterImpl(@Autowired(required = false) List<UserSubjectProvider> springBeanProviders) {
    Proxy.INSTANCE = this;
    List<UserSubjectProvider> providers = new ArrayList<>();
    
    // 1. 添加Spring Bean Provider
    if (springBeanProviders != null) {
      providers.addAll(springBeanProviders);
    }
    
    // 2. 添加SPI Provider
    providers.addAll(SPI.getSortedServiceStream(UserSubjectProvider.class)
        .collect(Collectors.toList()));
    
    // 3. 按@Priority排序（数值越大优先级越高）
    this.allProviders = providers.stream()
        .sorted(Comparator.comparing(this::getPriority).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public Optional<UserSubject> getByUser(String userIdentity) {
    return allProviders
        .stream()
        .map(provider -> provider.getByUser(userIdentity))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @Override
  public Optional<UserSubject> getByUser(String userIdentity, String subjectType) {
    return allProviders
        .stream()
        .map(provider -> provider.getByUser(userIdentity, subjectType))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @Override
  public List<UserSubject> getAllByUser(String userIdentity) {
    return allProviders
        .stream()
        .flatMap(provider -> provider.getAllByUser(userIdentity).stream())
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, List<UserSubject>> getAllByUsers(Collection<String> userIdentities) {
    Map<String, List<UserSubject>> result = new HashMap<>();
    
    for (UserSubjectProvider provider : allProviders) {
      Map<String, List<UserSubject>> providerResult = provider.getAllByUsers(userIdentities);
      for (Map.Entry<String, List<UserSubject>> entry : providerResult.entrySet()) {
        result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
      }
    }
    
    return result;
  }

  @Override
  public Map<String, UserSubject> getByUsers(Collection<String> userIdentities, String subjectType) {
    Map<String, UserSubject> result = new HashMap<>();
    
    for (UserSubjectProvider provider : allProviders) {
      Map<String, UserSubject> providerResult = provider.getByUsers(userIdentities, subjectType);
      for (Map.Entry<String, UserSubject> entry : providerResult.entrySet()) {
        result.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }
    
    return result;
  }

  @Override
  public Optional<UserSubject> getBySubject(String subjectType, String subjectId) {
    return allProviders
        .stream()
        .map(provider -> provider.getBySubject(subjectType, subjectId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @Override
  public List<UserSubject> getAll() {
    return allProviders
        .stream()
        .flatMap(provider -> provider.getAll().stream())
        .collect(Collectors.toList());
  }

  /**
   * 获取Provider优先级
   */
  private int getPriority(UserSubjectProvider provider) {
    Priority priority = provider.getClass().getAnnotation(Priority.class);
    return priority != null ? priority.value() : 0;
  }

  public static class Proxy implements UserSubjectCenter {
    private static UserSubjectCenterImpl INSTANCE;

    @Override
    public Optional<UserSubject> getByUser(String userIdentity) {
      return INSTANCE.getByUser(userIdentity);
    }

    @Override
    public Optional<UserSubject> getByUser(String userIdentity, String subjectType) {
      return INSTANCE.getByUser(userIdentity, subjectType);
    }

    @Override
    public List<UserSubject> getAllByUser(String userIdentity) {
      return INSTANCE.getAllByUser(userIdentity);
    }

    @Override
    public Map<String, List<UserSubject>> getAllByUsers(Collection<String> userIdentities) {
      return INSTANCE.getAllByUsers(userIdentities);
    }

    @Override
    public Map<String, UserSubject> getByUsers(Collection<String> userIdentities, String subjectType) {
      return INSTANCE.getByUsers(userIdentities, subjectType);
    }

    @Override
    public Optional<UserSubject> getBySubject(String subjectType, String subjectId) {
      return INSTANCE.getBySubject(subjectType, subjectId);
    }

    @Override
    public List<UserSubject> getAll() {
      return INSTANCE.getAll();
    }
  }
}
