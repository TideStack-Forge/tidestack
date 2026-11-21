package com.ouroboros.menu.model;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;

import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;

/**
 * @author liansz
 */
public class TreeMenuModelVo implements MenuModel {
  private final MenuModel menuModel;
  private final boolean pageAuthorized;

  private List<TreeMenuModelVo> children = new ArrayList<>();

  public TreeMenuModelVo(MenuModel menuModel) {
    this(menuModel, true);
  }

  public TreeMenuModelVo(MenuModel menuModel, boolean pageAuthorized) {
    this.menuModel = menuModel;
    this.pageAuthorized = pageAuthorized;
  }

  @Override
  public String getUrl() {
    return pageAuthorized ? menuModel.getUrl() : null;
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

  @Override
  public MenuScope getScope() {
    return menuModel.getScope();
  }

  public List<TreeMenuModelVo> getChildren() {
    return children;
  }

  public void setChildren(List<TreeMenuModelVo> children) {
    this.children = children;
  }
}
