package com.ouroboros.appmodule.dev.controller.vo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.util.TreeNode;

@JsonDeserialize(as = TreeNodeVO.class)
public class TreeNodeVO implements AppModule, TreeNode<TreeNodeVO> {
  List<TreeNodeVO> children;
  private String parent;
  private String fullName;
  private String title;
  private String description;
  private String icon;
  private Integer order;
  private String menu;
  private List<String> subMenus;
  private List<String> authorities;

  public TreeNodeVO() {
  }

  private TreeNodeVO(AppModule parent, AppModule appModule) {
    this.parent = parent.getFullName();
    this.fullName = appModule.getFullName();
    this.title = appModule.getTitle();
    this.description = appModule.getDescription();
    this.icon = appModule.getIcon();
    this.order = appModule.getOrder();
    this.menu = appModule.getMenu();
    this.subMenus = appModule.getSubMenus();
    this.authorities = appModule.getAuthorities();
  }

  @SuppressWarnings("SimplifyOptionalCallChains")
  @Override
  public List<TreeNodeVO> getChildren() {
    var children = Optional.ofNullable(this.children)
        .map(List::stream)
        .orElseGet(Stream::empty)
        .map(c -> new TreeNodeVO(this, c))
        .collect(Collectors.toList());
    reorder(children);
    return children;
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  @Override
  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getIcon() {
    return icon;
  }

  @Override
  public Integer getOrder() {
    return order == null ? 0 : order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  @Override
  public String getMenu() {
    return menu;
  }

  @Override
  public List<String> getSubMenus() {
    return subMenus;
  }

  @Override
  public List<String> getAuthorities() {
    return authorities;
  }

  // 最小交换算法重新设置Order
  public static void reorder(List<TreeNodeVO> nodeList) {
    if (nodeList.size() < 2) {
      return;
    }
    for (int i = 0; i < nodeList.size(); i++) {
      // 如果是第一个，检查 order 是否大于下一个的order，如果是，则将其order设置为下一个的 order - 1
      if (i == 0) {
        if (nodeList.get(i).getOrder() > nodeList.get(i + 1).getOrder()) {
          nodeList.get(i).setOrder(nodeList.get(i + 1).getOrder() - 1);
        }
      }
      // 否则检查当前项的 order 是否小于或等于上一项的 order，如果是，则将其 order 设置为上一项的 order + 1
      else {
        if (nodeList.get(i).getOrder() <= nodeList.get(i - 1).getOrder()) {
          nodeList.get(i).setOrder(nodeList.get(i - 1).getOrder() + 1);
        }
      }
    }
  }
}
