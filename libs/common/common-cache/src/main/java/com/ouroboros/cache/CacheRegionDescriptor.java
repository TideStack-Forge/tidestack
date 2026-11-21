package com.ouroboros.cache;

import java.util.ArrayList;
import java.util.List;

public class CacheRegionDescriptor {
  private String name;
  private String displayName;
  private String providerType;
  private Boolean inspectable;
  private Boolean hasCache;
  private Long entryCount;
  private String entryCountMode;
  private List<CacheRegionDescriptor> children = new ArrayList<>();

  public String getName() {
    return name;
  }

  public CacheRegionDescriptor setName(String name) {
    this.name = name;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public CacheRegionDescriptor setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getProviderType() {
    return providerType;
  }

  public CacheRegionDescriptor setProviderType(String providerType) {
    this.providerType = providerType;
    return this;
  }

  public Boolean getInspectable() {
    return inspectable;
  }

  public CacheRegionDescriptor setInspectable(Boolean inspectable) {
    this.inspectable = inspectable;
    return this;
  }

  public Boolean getHasCache() {
    return hasCache;
  }

  public CacheRegionDescriptor setHasCache(Boolean hasCache) {
    this.hasCache = hasCache;
    return this;
  }

  public Long getEntryCount() {
    return entryCount;
  }

  public CacheRegionDescriptor setEntryCount(Long entryCount) {
    this.entryCount = entryCount;
    return this;
  }

  public String getEntryCountMode() {
    return entryCountMode;
  }

  public CacheRegionDescriptor setEntryCountMode(String entryCountMode) {
    this.entryCountMode = entryCountMode;
    return this;
  }

  public List<CacheRegionDescriptor> getChildren() {
    return children;
  }

  public CacheRegionDescriptor setChildren(List<CacheRegionDescriptor> children) {
    this.children = children == null ? new ArrayList<>() : children;
    return this;
  }
}
