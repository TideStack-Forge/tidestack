package com.ouroboros.enterprise.wecom;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;

/** Provider-owned non-sensitive connection contract, independent of workflow capabilities. */
public final class WecomConnectionProvider implements EnterpriseConnectionProvider {
  @Override
  public String provider() { return "wecom"; }
  @Override public String displayName() { return "企业微信"; }

  @Override
  public int maxConnections() { return 1; }

  @Override
  public Set<String> connectionIdentityFields() { return Set.of("corpId"); }

  @Override
  public Set<String> connectionConfigFields() {
    return Set.of("corpId");
  }

  @Override
  public List<Map<String, Object>> credentialFields() {
    return List.of(field("agentId", "AgentID", true), field("secret", "应用 Secret", true),
        field("callbackToken", "回调 Token", false), field("encodingAesKey", "回调 EncodingAESKey", false));
  }

  private Map<String, Object> field(String name, String label, boolean required) {
    return Map.of("name", name, "label", label, "required", required);
  }

  @Override
  public Map<String, Object> connectionSchema() {
    return Map.of(
        "provider", provider(),
        "fields", java.util.List.of(Map.of(
            "name", "corpId",
            "type", "string",
            "label", "企业标识",
            "required", true,
            "description", "企业微信授权企业 corpId")));
  }

  @Override
  public void validateConnectionConfig(Map<String, Object> config) {
    Object corpId = config == null ? null : config.get("corpId");
    if (corpId == null || String.valueOf(corpId).isBlank()) {
      throw new BusinessException(
          "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
              + "] 企业微信连接缺少 corpId");
    }
  }
}
