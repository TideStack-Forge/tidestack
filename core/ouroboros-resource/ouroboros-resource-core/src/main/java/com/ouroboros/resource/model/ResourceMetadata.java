package com.ouroboros.resource.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resource metadata stored in sidecar or index files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceMetadata {

  private ResourceScope scope;
  private String key;
  private String name;
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

  public ResourceMetadata setScope(ResourceScope scope) {
    this.scope = scope;
    return this;
  }

  public String getKey() {
    return key;
  }

  public ResourceMetadata setKey(String key) {
    this.key = normalize(key);
    return this;
  }

  public String getName() {
    if (name != null) {
      return name;
    }
    if (key == null || key.isEmpty()) {
      return null;
    }
    int lastSlash = key.lastIndexOf('/');
    return lastSlash < 0 ? key : key.substring(lastSlash + 1);
  }

  public ResourceMetadata setName(String name) {
    this.name = name;
    return this;
  }

  public String getContentType() {
    return contentType;
  }

  public ResourceMetadata setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public Long getSize() {
    return size;
  }

  public ResourceMetadata setSize(Long size) {
    this.size = size;
    return this;
  }

  public String getChecksum() {
    return checksum;
  }

  public ResourceMetadata setChecksum(String checksum) {
    this.checksum = checksum;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public ResourceMetadata setVersionTag(String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public ResourceMetadata setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ResourceMetadata setDescription(String description) {
    this.description = description;
    return this;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public ResourceMetadata setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    return this;
  }

  public ResourceLayer getLayer() {
    return layer;
  }

  public ResourceMetadata setLayer(ResourceLayer layer) {
    this.layer = layer;
    return this;
  }

  public Instant getLastModifiedAt() {
    return lastModifiedAt;
  }

  public ResourceMetadata setLastModifiedAt(Instant lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public ResourceMetadata setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
    return this;
  }

  public ResourceDescriptor toDescriptor() {
    return new ResourceDescriptor()
        .setScope(scope)
        .setKey(key)
        .setFileName(getName())
        .setContentType(contentType)
        .setSize(size)
        .setChecksum(checksum)
        .setVersionTag(versionTag)
        .setDisplayName(displayName)
        .setDescription(description)
        .setMetadata(metadata)
        .setLayer(layer)
        .setLastModifiedAt(lastModifiedAt)
        .setLastModifiedBy(lastModifiedBy);
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
