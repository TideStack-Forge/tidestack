package com.ouroboros.dev.runtime.gateway.host;

public interface StableDomainHostResolver {
  String resolve(String appName, String profile);
}
