package com.ouroboros.appmodule.dev.controller.vo;

import java.util.List;

import com.ouroboros.appmodule.model.AppModuleTreeNode;

public class FormInitDataVO {
  List<AppModuleTreeNode> modules;
  List<RemovedModuleVO> removedModules;

  public FormInitDataVO(List<AppModuleTreeNode> modules, List<RemovedModuleVO> removedModules) {
    this.modules = modules;
    this.removedModules = removedModules;
  }

  public List<AppModuleTreeNode> getModules() {
    return modules;
  }

  public List<RemovedModuleVO> getRemovedModules() {
    return removedModules;
  }
}
