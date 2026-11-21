package com.ouroboros.data.dict.pojo;

import com.ouroboros.data.dict.DataDictStateTransition;

/**
 * @author liansz
 */
public class DefaultDataDictStateTransition implements DataDictStateTransition {

  /**
   * 迁移到的状态节点code
   */
  private String to;

  /**
   * 迁移条件
   */
  private String condition;

  @Override
  public String getTo() {
    return to;
  }

  public void setTo(String to) {
    this.to = to;
  }

  @Override
  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }
}
