package com.ouroboros.resource.dev.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ouroboros.resource.model.ResourceScope;

import java.util.HashMap;
import java.util.Map;

/**
 * Resource metadata model for development
 * This model is serializable and stored as JSON files in VCS
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceModel {

  private ResourceScope scope;
  private String key;
  private String fileName;
  private String directory;
  private String contentType;
  private String versionTag;
  private Long size;
  private String checksum;
  private String displayName;
  private String description;
  private Map<String, Object> metadata = new HashMap<>();

  public ResourceScope getScope() {
    return scope;
  }

  public ResourceModel setScope(ResourceScope scope) {
    this.scope = scope;
    return this;
  }

  public String getKey() {
    return key;
  }

  public ResourceModel setKey(String key) {
    this.key = normalize(key);
    syncPathSegments();
    return this;
  }

  public String getFileName() {
    return fileName;
  }

  public ResourceModel setFileName(String fileName) {
    this.fileName = fileName;
    return this;
  }

  public String getDirectory() {
    return directory;
  }

  public ResourceModel setDirectory(String directory) {
    this.directory = normalize(directory);
    return this;
  }

  public String getContentType() {
    return contentType;
  }

  public ResourceModel setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public ResourceModel setVersionTag(String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public Long getSize() {
    return size;
  }

  public ResourceModel setSize(Long size) {
    this.size = size;
    return this;
  }

  public String getChecksum() {
    return checksum;
  }

  public ResourceModel setChecksum(String checksum) {
    this.checksum = checksum;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public ResourceModel setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ResourceModel setDescription(String description) {
    this.description = description;
    return this;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public ResourceModel setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
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
