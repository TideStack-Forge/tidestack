package com.ouroboros.resource.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resource index (index.json) for a directory
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceIndex {

  private String path;
  private ResourceScope scope;
  private String directory;
  private ResourceLayer layer;
  private List<ResourceMetadata> resources = new ArrayList<>();
  private Instant updatedAt;

  public String getPath() {
    return path;
  }

  public ResourceIndex setPath(String path) {
    this.path = path;
    return this;
  }

  public ResourceScope getScope() {
    return scope;
  }

  public ResourceIndex setScope(ResourceScope scope) {
    this.scope = scope;
    return this;
  }

  public String getDirectory() {
    return directory;
  }

  public ResourceIndex setDirectory(String directory) {
    this.directory = directory;
    return this;
  }

  public ResourceLayer getLayer() {
    return layer;
  }

  public ResourceIndex setLayer(ResourceLayer layer) {
    this.layer = layer;
    return this;
  }

  public List<ResourceMetadata> getResources() {
    return resources;
  }

  public ResourceIndex setResources(List<ResourceMetadata> resources) {
    this.resources = resources == null ? new ArrayList<>() : new ArrayList<>(resources);
    return this;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public ResourceIndex setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
