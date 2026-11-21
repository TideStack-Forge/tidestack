package com.ouroboros.workflow.approval.external;

/**
 * 抄送目标。
 *
 * @param targetType 抄送对象类型（user / role / department）
 * @param targetId   对象 ID；type=user 时为平台规范用户 ID（由适配器经 ExternalUserIdentityMapper 翻译）
 * @param position   抄送时机：START / FINISH / START_FINISH
 */
public record CcTarget(String targetType, String targetId, String position) {

  /** 发起时抄送。 */
  public static final String POSITION_START = "START";

  /** 结束时抄送。 */
  public static final String POSITION_FINISH = "FINISH";

  /** 发起与结束时均抄送。 */
  public static final String POSITION_START_FINISH = "START_FINISH";
}
