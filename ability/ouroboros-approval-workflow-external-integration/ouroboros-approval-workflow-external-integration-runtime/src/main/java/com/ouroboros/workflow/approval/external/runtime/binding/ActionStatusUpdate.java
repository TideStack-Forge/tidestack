package com.ouroboros.workflow.approval.external.runtime.binding;

/** 外部动作完成本地绑定时的条件更新结果。 */
public enum ActionStatusUpdate {
  UPDATED,
  ALREADY_APPLIED,
  CONFLICT
}
