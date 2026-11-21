package com.ouroboros.resource.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resource descriptor without file content.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceDescriptor {

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
  private ResourceLayer layer;
  private Instant lastModifiedAt;
  private String lastModifiedBy;

  public ResourceScope getScope() {
    return scope;
  }

  public ResourceDescriptor setScope(ResourceScope scope) {
    this.scope = scope;
    return this;
  }

  public String getKey() {
    return key;
  }

  public ResourceDescriptor setKey(String key) {
    this.key = normalize(key);
    syncPathSegments();
    return this;
  }

  public String getFileName() {
    return fileName;
  }

  public ResourceDescriptor setFileName(String fileName) {
    this.fileName = fileName;
    return this;
  }

  public String getDirectory() {
    return directory;
  }

  public ResourceDescriptor setDirectory(String directory) {
    this.directory = normalize(directory);
    return this;
  }

  public String getContentType() {
    return contentType;
  }

  public ResourceDescriptor setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public Long getSize() {
    return size;
  }

  public ResourceDescriptor setSize(Long size) {
    this.size = size;
    return this;
  }

  public String getChecksum() {
    return checksum;
  }

  public ResourceDescriptor setChecksum(String checksum) {
    this.checksum = checksum;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public ResourceDescriptor setVersionTag(String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public ResourceDescriptor setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ResourceDescriptor setDescription(String description) {
    this.description = description;
    return this;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public ResourceDescriptor setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    return this;
  }

  public ResourceDescriptor putMetadata(String key, Object value) {
    this.metadata.put(key, value);
    return this;
  }

  public ResourceLayer getLayer() {
    return layer;
  }

  public ResourceDescriptor setLayer(ResourceLayer layer) {
    this.layer = layer;
    return this;
  }

  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  public ResourceDescriptor setLastModifiedAt(Instant lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public ResourceDescriptor setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
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
