package com.ouroboros.enterprise.feishu;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;

/** Provider-owned non-sensitive connection contract, independent of workflow capabilities. */
public final class FeishuConnectionProvider implements EnterpriseConnectionProvider {
  @Override
  public java.util.List<Map<String, Object>> credentialFields() {
    return java.util.List.of(field("appId", "App ID", true), field("appSecret", "App Secret", true),
        field("verificationToken", "回调 Verification Token", false), field("encryptKey", "回调 Encrypt Key", false));
  }

  private Map<String, Object> field(String name, String label, boolean required) {
    return Map.of("name", name, "label", label, "required", required);
  }
  @Override
  public String provider() { return "feishu"; }
  @Override public String displayName() { return "飞书"; }

  @Override
  public int maxConnections() { return 1; }

  @Override
  public Set<String> connectionConfigFields() {
    return Set.of("departmentId");
  }

  @Override
  public Map<String, Object> connectionSchema() {
    return Map.of(
        "provider", provider(),
        "fields", java.util.List.of(Map.of(
            "name", "departmentId",
            "type", "string",
            "label", "默认发起部门",
            "required", false,
            "description", "可选的默认发起部门；未填写时由发起命令提供")));
  }
}
