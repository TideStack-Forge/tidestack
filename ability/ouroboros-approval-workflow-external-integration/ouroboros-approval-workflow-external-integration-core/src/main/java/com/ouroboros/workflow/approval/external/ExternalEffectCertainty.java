package com.ouroboros.workflow.approval.external;

/**
 * 远程操作对副作用是否已经发生的确定性。
 *
 * <p>适配器必须在跨网络边界返回失败时提供该语义；调用方不得从异常类型或 message 推断。
 */
public enum ExternalEffectCertainty {
  /** 可以证明远程系统没有接受该操作。 */
  DEFINITELY_NOT_APPLIED,
  /** 请求结果未知，远程系统可能已经接受。 */
  UNKNOWN,
  /** 可以证明远程系统已经接受该操作。 */
  APPLIED
}
