package com.ouroboros.resource.manage.dto;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceScope;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuntimeResourceItem {

  private ResourceScope scope;
  private String key;
  private String fileName;
  private String directory;
  private String contentType;
  private Long size;
  private String checksum;
  private String versionTag;
  private String displayName;
  private String description;
  private Map<String, Object> metadata = new HashMap<>();
  private Instant lastModifiedAt;
  private String lastModifiedBy;
  private ResourceLayer effectiveLayer;
  private boolean hasOverride;
  private boolean defaultExists;

  public ResourceScope getScope() {
    return scope;
  }

  public RuntimeResourceItem setScope(ResourceScope scope) {
    this.scope = scope;
    return this;
  }

  public String getKey() {
    return key;
  }

  public RuntimeResourceItem setKey(String key) {
    this.key = normalize(key);
    syncPathSegments();
    return this;
  }

  public String getFileName() {
    return fileName;
  }

  public RuntimeResourceItem setFileName(String fileName) {
    this.fileName = fileName;
    return this;
  }

  public String getDirectory() {
    return directory;
  }

  public RuntimeResourceItem setDirectory(String directory) {
    this.directory = normalize(directory);
    return this;
  }

  public String getContentType() {
    return contentType;
  }

  public RuntimeResourceItem setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public Long getSize() {
    return size;
  }

  public RuntimeResourceItem setSize(Long size) {
    this.size = size;
    return this;
  }

  public String getChecksum() {
    return checksum;
  }

  public RuntimeResourceItem setChecksum(String checksum) {
    this.checksum = checksum;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public RuntimeResourceItem setVersionTag(String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public RuntimeResourceItem setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public RuntimeResourceItem setDescription(String description) {
    this.description = description;
    return this;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public RuntimeResourceItem setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    return this;
  }

  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  public RuntimeResourceItem setLastModifiedAt(Instant lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public RuntimeResourceItem setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
    return this;
  }

  public ResourceLayer getEffectiveLayer() {
    return effectiveLayer;
  }

  public RuntimeResourceItem setEffectiveLayer(ResourceLayer effectiveLayer) {
    this.effectiveLayer = effectiveLayer;
    return this;
  }

  public boolean isHasOverride() {
    return hasOverride;
  }

  public RuntimeResourceItem setHasOverride(boolean hasOverride) {
    this.hasOverride = hasOverride;
    return this;
  }

  public boolean isDefaultExists() {
    return defaultExists;
  }

  public RuntimeResourceItem setDefaultExists(boolean defaultExists) {
    this.defaultExists = defaultExists;
    return this;
  }

  @JsonIgnore
  public String getScopedPath() {
    if (scope == null) {
      return key;
    }
    if (key == null || key.isEmpty()) {
      return scope.getValue();
    }
    return scope.getValue() + "/" + key;
  }

  private void syncPathSegments() {
    if (key == null || key.isEmpty()) {
      this.directory = "";
      this.fileName = "";
      return;
    }
    int lastSlash = key.lastIndexOf('/');
    if (lastSlash < 0) {
      this.directory = "";
      this.fileName = key;
      return;
    }
    this.directory = key.substring(0, lastSlash);
    this.fileName = key.substring(lastSlash + 1);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.startsWith("/") ? value.substring(1) : value;
    normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    return normalized;
  }
}
