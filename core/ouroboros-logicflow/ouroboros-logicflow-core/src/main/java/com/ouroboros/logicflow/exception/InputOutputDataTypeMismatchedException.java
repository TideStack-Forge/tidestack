package com.ouroboros.logicflow.exception;

import com.ouroboros.logicflow.model.FlowModelMeta;

public class InputOutputDataTypeMismatchedException extends FlowDesignException {
  public InputOutputDataTypeMismatchedException(FlowModelMeta model) {
    super("输入输出数据类型不匹配", model);
  }
}
