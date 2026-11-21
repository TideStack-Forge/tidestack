package com.ouroboros.data.dict;

/**
 * @author sunquan
 */
public interface DataDictStateTransition {

  /**
   * 迁移到的状态节点code
   */
  String getTo();

  /**
   * 迁移条件
   */
  String getCondition();
}
