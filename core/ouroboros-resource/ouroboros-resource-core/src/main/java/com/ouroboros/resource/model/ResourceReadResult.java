package com.ouroboros.resource.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified resource read result.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceReadResult {

  private Resource effectiveResource;
  private ResourceDescriptor effectiveDescriptor;
  private ResourceLayer effectiveLayer;
  private boolean defaultExists;
  private boolean hasOverride;
  private String source;

  public Resource getEffectiveResource() {
    return effectiveResource;
  }

  public ResourceReadResult setEffectiveResource(Resource effectiveResource) {
    this.effectiveResource = effectiveResource;
    if (effectiveResource != null && this.effectiveDescriptor == null) {
      this.effectiveDescriptor = effectiveResource.getDescriptor();
    }
    return this;
  }

  public ResourceDescriptor getEffectiveDescriptor() {
    if (effectiveDescriptor != null) {
      return effectiveDescriptor;
    }
    return effectiveResource == null ? null : effectiveResource.getDescriptor();
  }

  public ResourceReadResult setEffectiveDescriptor(ResourceDescriptor effectiveDescriptor) {
    this.effectiveDescriptor = effectiveDescriptor;
    return this;
  }

  public ResourceLayer getEffectiveLayer() {
    return effectiveLayer;
  }

  public ResourceReadResult setEffectiveLayer(ResourceLayer effectiveLayer) {
    this.effectiveLayer = effectiveLayer;
    return this;
  }

  public boolean isDefaultExists() {
    return defaultExists;
  }

  public ResourceReadResult setDefaultExists(boolean defaultExists) {
    this.defaultExists = defaultExists;
    return this;
  }

  public boolean isHasOverride() {
    return hasOverride;
  }

  public ResourceReadResult setHasOverride(boolean hasOverride) {
    this.hasOverride = hasOverride;
    return this;
  }

  public String getSource() {
    return source;
  }

  public ResourceReadResult setSource(String source) {
    this.source = source;
    return this;
  }
}
