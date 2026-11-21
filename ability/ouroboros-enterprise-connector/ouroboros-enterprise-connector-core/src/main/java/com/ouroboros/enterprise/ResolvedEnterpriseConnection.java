package com.ouroboros.enterprise;

import java.util.Map;

/**
 * 外部引擎连接配置（普通配置只保存非敏感字段，凭据由运行时解析为内存 typed credential）。
 *
 * @param connectionId 连接 ID
 * @param config       已通过 provider schema 校验的非敏感连接字段；provider、revision
 *                     和 fingerprint 由连接读取器加入
 * @param credential   运行时解析出的 typed credential；不得写入普通 config 或持久化模型
 */
public record ResolvedEnterpriseConnection(
    String connectionId,
    Map<String, Object> config,
    @com.fasterxml.jackson.annotation.JsonIgnore EnterpriseCredential credential) {

  public ResolvedEnterpriseConnection {
    config = config == null ? Map.of() : Map.copyOf(config);
  }

  @Override
  public String toString() {
    return "ResolvedEnterpriseConnection[connectionId=" + connectionId + ", provider=" + provider()
        + ", revision=" + revision() + ", credential=****]";
  }

  /**
   * 连接所属 provider。provider 是连接快照的路由事实，不由调用方另行传入。
   */
  public String provider() {
    Object value = config == null ? null : config.get("provider");
    return value == null ? null : String.valueOf(value);
  }

  /** 连接快照 revision；缺失时表示调用方构造了未持久化的测试连接。 */
  public String revision() {
    Object value = config == null ? null : config.get("revision");
    return value == null ? null : String.valueOf(value);
  }

}
