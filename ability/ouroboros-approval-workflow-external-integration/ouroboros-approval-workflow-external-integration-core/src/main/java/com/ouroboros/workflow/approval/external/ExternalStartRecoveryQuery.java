package com.ouroboros.workflow.approval.external;

import java.util.Objects;

/**
 * 外部启动结果查询的稳定业务身份。
 *
 * <p>查询不得依赖本地 lease token 或随机 worker 标识；适配器应使用幂等键或请求指纹在远端定位同一启动事实。
 */
public record ExternalStartRecoveryQuery(
    String idempotencyKey,
    String requestFingerprint,
    String scope,
    String flowKey,
    String businessRecordType,
    String businessRecordId,
    String mappingId,
    Integer mappingVersion) {

  public ExternalStartRecoveryQuery {
    if ((idempotencyKey == null || idempotencyKey.isBlank())
        && (requestFingerprint == null || requestFingerprint.isBlank())) {
      throw new IllegalArgumentException("外部启动恢复必须包含幂等键或请求指纹");
    }
    Objects.requireNonNull(flowKey, "flowKey");
    Objects.requireNonNull(mappingId, "mappingId");
    Objects.requireNonNull(mappingVersion, "mappingVersion");
  }
}
