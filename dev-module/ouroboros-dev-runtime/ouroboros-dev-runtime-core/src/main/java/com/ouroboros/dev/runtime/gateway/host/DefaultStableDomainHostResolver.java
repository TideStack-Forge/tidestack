package com.ouroboros.dev.runtime.gateway.host;

public class DefaultStableDomainHostResolver implements StableDomainHostResolver {
  private final String hostSuffix;

  public DefaultStableDomainHostResolver(String hostSuffix) {
    this.hostSuffix = hostSuffix;
  }

  @Override
  public String resolve(String appName, String profile) {
    if (!hasText(hostSuffix) || !hasText(appName) || !hasText(profile)) {
      return null;
    }
    return sanitizeHostPart(appName) + "-" + sanitizeHostPart(profile) + "." + hostSuffix.trim().toLowerCase();
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  // Keep this aligned with KubernetesRuntimeResourceFactory.sanitizeHostPart.
  private String sanitizeHostPart(String value) {
    if (!hasText(value)) {
      return "x";
    }
    return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
  }
}
