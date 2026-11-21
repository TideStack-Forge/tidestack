package com.ouroboros.enterprise.manage;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.ouroboros.uischema.UISchemaProvider;

/** Original Schema SPI contributes a static service reference; no container state or secret read. */
public final class EnterpriseConnectionUISchemaProvider implements UISchemaProvider {
  private static final Pattern PROVIDER = Pattern.compile("[a-z0-9-]+");

  @Override
  public Optional<Object> getUISchema(String path) {
    if (path == null || !path.startsWith(EnterpriseConnectionIntegrationContributor.PATH)) { return Optional.empty(); }
    String provider = path.substring(EnterpriseConnectionIntegrationContributor.PATH.length());
    if (!PROVIDER.matcher(provider).matches()) { return Optional.empty(); }
    return Optional.of(Map.of("type", "service", "schemaApi",
        Map.of("method", "get", "url", "/api/sys/enterprise-connections/providers/" + provider + "/management-schema")));
  }

  @Override public Map<String, Object> getUISchemaMap() { return Map.of(); }
}
