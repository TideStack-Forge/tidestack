package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class RacingConditionDetectedException extends FlowDesignException {
  public RacingConditionDetectedException(FlowModelMeta model) {
    super("存在竞争条件", model);
  }
}
