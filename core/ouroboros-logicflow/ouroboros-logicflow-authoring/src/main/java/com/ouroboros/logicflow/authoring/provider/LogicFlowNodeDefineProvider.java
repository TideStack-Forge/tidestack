package com.ouroboros.logicflow.authoring.provider;

import java.util.List;

import com.ouroboros.logicflow.authoring.model.NodeDefine;

public interface LogicFlowNodeDefineProvider {
  List<NodeDefine> getNodeDefines();
}
