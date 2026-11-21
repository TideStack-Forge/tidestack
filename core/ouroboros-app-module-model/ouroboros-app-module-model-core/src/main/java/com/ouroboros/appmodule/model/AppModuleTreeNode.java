package com.ouroboros.appmodule.model;

import java.util.List;
import java.util.Optional;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleDiff;
import com.ouroboros.util.TreeNode;

public class AppModuleTreeNode implements AppModule, TreeNode<AppModuleTreeNode> {
  private final AppModule appModule;
  private List<AppModuleTreeNode> children;

  public AppModuleTreeNode(AppModule appModule) {
    this.appModule = appModule;
  }

  @Override
  public String getFullName() {
    return appModule.getFullName();
  }

  @Override
  public String getParent() {
    return appModule.getParent();
  }

  @Override
  public String getName() {
    return appModule.getName();
  }

  @Override
  public String getTitle() {
    return appModule.getTitle();
  }

  @Override
  public String getDescription() {
    return appModule.getDescription();
  }

  @Override
  public String getIcon() {
    return appModule.getIcon();
  }

  @Override
  public Integer getOrder() {
    return appModule.getOrder();
  }

  @Override
  public String getMenu() {
    return appModule.getMenu();
  }

  @Override
  public List<String> getSubMenus() {
    return appModule.getSubMenus();
  }

  @Override
  public List<String> getAuthorities() {
    return appModule.getAuthorities();
  }

  @Override
  public Boolean isSameModule(AppModule another) {
    return appModule.isSameModule(another);
  }

  @Override
  public Optional<AppModuleDiff> diff(AppModule originalModule) {
    return appModule.diff(originalModule);
  }

  @Override
  public List<AppModuleTreeNode> getChildren() {
    return children;
  }

  public AppModuleTreeNode setChildren(List<AppModuleTreeNode> children) {
    this.children = children;
    return this;
  }
}
