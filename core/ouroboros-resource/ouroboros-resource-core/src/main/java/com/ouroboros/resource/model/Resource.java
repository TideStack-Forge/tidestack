package com.ouroboros.resource.model;

import java.io.InputStream;
import java.util.Map;

/**
 * Represents a resource in the system
 * Resources are treated as files without type distinction
 */
public class Resource {

  private final ResourceDescriptor descriptor;
  private final InputStream content;

  private Resource(Builder builder) {
    this.descriptor = builder.descriptor;
    this.content = builder.content;
  }

  public ResourceDescriptor getDescriptor() {
    return descriptor;
  }

  public ResourceScope getScope() {
    return descriptor.getScope();
  }

  public String getKey() {
    return descriptor.getKey();
  }

  public ResourceLayer getLayer() {
    return descriptor.getLayer();
  }

  public String getPath() {
    return descriptor.getScopedPath();
  }

  public InputStream getContent() {
    return content;
  }

  public Map<String, Object> getMetadata() {
    return descriptor.getMetadata();
  }

  public Object getMetadata(String key) {
    return descriptor.getMetadata().get(key);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ResourceDescriptor descriptor = new ResourceDescriptor()
        .setLayer(ResourceLayer.APP_DEFAULT);
    private InputStream content;

    public Builder descriptor(ResourceDescriptor descriptor) {
      this.descriptor = descriptor == null ? new ResourceDescriptor() : descriptor;
      if (this.descriptor.getLayer() == null) {
        this.descriptor.setLayer(ResourceLayer.APP_DEFAULT);
      }
      return this;
    }

    public Builder scope(ResourceScope scope) {
      this.descriptor.setScope(scope);
      return this;
    }

    public Builder key(String key) {
      this.descriptor.setKey(key);
      return this;
    }

    public Builder layer(ResourceLayer layer) {
      this.descriptor.setLayer(layer);
      return this;
    }

    public Builder path(String path) {
      this.descriptor.setScope(extractScope(path));
      this.descriptor.setKey(extractKey(path));
      return this;
    }

    public Builder content(InputStream content) {
      this.content = content;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.descriptor.setMetadata(metadata);
      return this;
    }

    public Builder metadata(String key, Object value) {
      this.descriptor.putMetadata(key, value);
      return this;
    }

    public Resource build() {
      if (descriptor.getScope() == null) {
        throw new IllegalArgumentException("Resource scope is required");
      }
      if (descriptor.getKey() == null || descriptor.getKey().isEmpty()) {
        throw new IllegalArgumentException("Resource key is required");
      }
      if (content == null) {
        throw new IllegalArgumentException("Resource content is required");
      }
      return new Resource(this);
    }
  }

  @Override
  public String toString() {
    return "Resource{" +
        "descriptor=" + descriptor +
        '}';
  }

  private static ResourceScope extractScope(String path) {
    String normalized = normalize(path);
    int firstSlash = normalized.indexOf('/');
    String scopeValue = firstSlash < 0 ? normalized : normalized.substring(0, firstSlash);
    return ResourceScope.fromValue(scopeValue);
  }

  private static String extractKey(String path) {
    String normalized = normalize(path);
    int firstSlash = normalized.indexOf('/');
    if (firstSlash < 0 || firstSlash == normalized.length() - 1) {
      return "";
    }
    return normalized.substring(firstSlash + 1);
  }

  private static String normalize(String path) {
    if (path == null) {
      return "";
    }
    String normalized = path.startsWith("/") ? path.substring(1) : path;
    normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    return normalized;
  }
}
