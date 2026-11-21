package com.ouroboros.security.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.SPI;

/**
 * 主体中心实现
 * 支持Spring Bean + SPI Provider聚合，按优先级排序
 *
 * @author songmingxu
 */
@Component
public class SubjectCenterImpl implements SubjectCenter {

  private final List<SubjectProvider> allProviders;
  private final Map<String, List<SubjectProvider>> providersByType;

  public SubjectCenterImpl(@Autowired(required = false) List<SubjectProvider> springBeanProviders) {
    Proxy.INSTANCE = this;
    List<SubjectProvider> providers = new ArrayList<>();
    
    // 1. 添加Spring Bean Provider
    if (springBeanProviders != null) {
      providers.addAll(springBeanProviders);
    }
    
    // 2. 添加SPI Provider
    providers.addAll(SPI.getSortedServiceStream(SubjectProvider.class)
        .collect(Collectors.toList()));
    
    // 3. 按@Priority排序（数值越大优先级越高）
    this.allProviders = providers.stream()
        .sorted(Comparator.comparing(this::getPriority).reversed())
        .collect(Collectors.toList());
    
    // 4. 按类型分组
    this.providersByType = allProviders.stream()
        .collect(Collectors.groupingBy(SubjectProvider::getSupportedType));
  }

  @Override
  public Optional<Subject> getByTypeAndId(String type, String id) {
    List<SubjectProvider> providers = providersByType.get(type);
    if (providers == null || providers.isEmpty()) {
      return Optional.empty();
    }
    
    // 按优先级遍历，返回第一个找到的结果
    return providers.stream()
        .map(provider -> provider.getById(id))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @Override
  public Optional<Subject> getByTypeAndId(String type, String id, String providerName) {
    List<SubjectProvider> providers = providersByType.get(type);
    if (providers == null || providers.isEmpty()) {
      return Optional.empty();
    }
    
    return providers.stream()
        .filter(p -> providerName.equals(p.getProviderName()))
        .findFirst()
        .flatMap(p -> p.getById(id));
  }

  @Override
  public List<Subject> getByTypeAndIds(String type, Collection<String> ids) {
    List<SubjectProvider> providers = providersByType.get(type);
    if (providers == null || providers.isEmpty()) {
      return Collections.emptyList();
    }
    
    // 聚合所有Provider的结果
    return providers.stream()
        .flatMap(provider -> provider.getByIds(ids).stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<Subject> getByType(String type) {
    List<SubjectProvider> providers = providersByType.get(type);
    if (providers == null || providers.isEmpty()) {
      return Collections.emptyList();
    }
    
    // 聚合所有Provider的结果
    return providers.stream()
        .flatMap(provider -> provider.getAll().stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getSupportedTypes() {
    return new ArrayList<>(providersByType.keySet());
  }

  @Override
  public List<String> getProviderNames(String type) {
    List<SubjectProvider> providers = providersByType.get(type);
    if (providers == null || providers.isEmpty()) {
      return Collections.emptyList();
    }
    
    return providers.stream()
        .map(SubjectProvider::getProviderName)
        .collect(Collectors.toList());
  }

  /**
   * 获取Provider优先级
   */
  private int getPriority(SubjectProvider provider) {
    Priority priority = provider.getClass().getAnnotation(Priority.class);
    return priority != null ? priority.value() : 0;
  }

  public static class Proxy implements SubjectCenter {
    private static SubjectCenterImpl INSTANCE;

    @Override
    public Optional<Subject> getByTypeAndId(String type, String id) {
      return INSTANCE.getByTypeAndId(type, id);
    }

    @Override
    public Optional<Subject> getByTypeAndId(String type, String id, String providerName) {
      return INSTANCE.getByTypeAndId(type, id, providerName);
    }

    @Override
    public List<Subject> getByTypeAndIds(String type, Collection<String> ids) {
      return INSTANCE.getByTypeAndIds(type, ids);
    }

    @Override
    public List<Subject> getByType(String type) {
      return INSTANCE.getByType(type);
    }

    @Override
    public List<String> getSupportedTypes() {
      return INSTANCE.getSupportedTypes();
    }

    @Override
    public List<String> getProviderNames(String type) {
      return INSTANCE.getProviderNames(type);
    }
  }
}
