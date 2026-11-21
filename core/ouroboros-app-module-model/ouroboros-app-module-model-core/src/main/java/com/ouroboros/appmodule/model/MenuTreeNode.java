package com.ouroboros.appmodule.model;

import java.beans.Transient;
import java.util.List;

import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;
import com.ouroboros.util.TreeNode;

@SuppressWarnings({"unused"})
public class MenuTreeNode implements MenuModel, TreeNode<MenuTreeNode> {
  private final MenuModel menuModel;
  private List<MenuTreeNode> children;

  public MenuTreeNode(MenuModel menuModel) {
    this.menuModel = menuModel;
  }

  @Override
  public List<MenuTreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<MenuTreeNode> children) {
    this.children = children;
  }

  @Override
  public String getUrl() {
    return menuModel.getUrl();
  }

  @Override
  public String getFullName() {
    return menuModel.getFullName();
  }

  @Override
  @Transient
  public String getParent() {
    return menuModel.getParent();
  }

  @Override
  @Transient
  public String getName() {
    return menuModel.getName();
  }

  @Override
  public String getIcon() {
    return menuModel.getIcon();
  }

  @Override
  public String getTitle() {
    return menuModel.getTitle();
  }

  @Override
  public MenuModelShowModeEnum getShowMode() {
    return menuModel.getShowMode();
  }

  @Override
  public Integer getOrder() {
    return menuModel.getOrder();
  }


}
