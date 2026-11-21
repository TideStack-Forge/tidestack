package com.ouroboros.enterprise.dingtalk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;

/** Provider-owned non-sensitive connection contract, independent of workflow capabilities. */
public final class DingtalkConnectionProvider implements EnterpriseConnectionProvider {
  @Override
  public String provider() { return "dingtalk"; }
  @Override public String displayName() { return "钉钉"; }

  @Override
  public int maxConnections() { return 1; }

  @Override
  public Set<String> connectionIdentityFields() { return Set.of("corpId", "agentId"); }

  @Override
  public Set<String> connectionConfigFields() {
    return Set.of("corpId", "agentId", "callbackMode");
  }

  @Override
  public List<Map<String, Object>> credentialFields() {
    return List.of(field("appKey", "AppKey", true), field("appSecret", "AppSecret", true),
        field("callbackToken", "回调 Token", false), field("encodingAesKey", "回调 EncodingAESKey", false));
  }

  private Map<String, Object> field(String name, String label, boolean required) {
    return Map.of("name", name, "label", label, "required", required);
  }

  @Override
  public Map<String, Object> connectionSchema() {
    return Map.of(
        "provider", provider(),
        "fields", List.of(Map.of(
            "name", "corpId",
            "type", "string",
            "label", "企业标识",
            "required", true,
            "description", "钉钉企业 corpId，用于回调归属检查和 AppLink"),
            Map.of("name", "agentId", "type", "string", "label", "应用 AgentID",
                "description", "发送工作通知时必填，使用当前 AppKey 对应自建应用的 AgentID。"),
            Map.of("name", "callbackMode", "type", "select", "label", "回调来源",
                "options", List.of(Map.of("label", "企业级回调地址注册（CorpID）", "value", "CORPORATE"),
                    Map.of("label", "应用后台事件订阅（AppKey）", "value", "APPLICATION")),
                "description", "须与钉钉后台的事件接入方式一致。已有配置未选择时沿用企业级回调。")));
  }

  @Override
  public void validateConnectionConfig(Map<String, Object> config) {
    Object corpId = config == null ? null : config.get("corpId");
    if (corpId == null || String.valueOf(corpId).isBlank()) {
      throw new BusinessException(
          "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
              + "] 钉钉连接缺少 corpId");
    }
    Object callbackMode = config.get("callbackMode");
    Object agentId = config.get("agentId");
    if (agentId != null && !String.valueOf(agentId).matches("[1-9][0-9]{0,18}")) {
      throw new BusinessException("钉钉应用 AgentID 必须为正整数");
    }
    if (callbackMode != null && !Set.of("CORPORATE", "APPLICATION").contains(callbackMode)) {
      throw new BusinessException("钉钉回调来源无效");
    }
  }
}
