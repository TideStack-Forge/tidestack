package com.ouroboros.appmodule.model;

import java.beans.Transient;
import java.util.List;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.util.TreeNode;

public class AuthorityTreeNode implements Authority, TreeNode<AuthorityTreeNode> {
  private final Authority authority;
  private List<AuthorityTreeNode> children;

  public AuthorityTreeNode(Authority authority) {
    this.authority = authority;
  }

  @Override
  public List<AuthorityTreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<AuthorityTreeNode> children) {
    this.children = children;
  }

  @Override
  public String getFullName() {
    return authority.getFullName();
  }

  @Override
  @Transient
  public String getParent() {
    return authority.getParent();
  }

  @Override
  @Transient
  public String getName() {
    return authority.getName();
  }

  @Override
  @Transient
  public Integer getLevel() {
    return authority.getLevel();
  }

  @Override
  public String getTitle() {
    return authority.getTitle();
  }

  @Override
  public AccessControlDefine getAccessControlDefine() {
    return authority.getAccessControlDefine();
  }

  @Override
  public List<String> getApis() {
    return authority.getApis();
  }

  @Override
  public List<String> getUiViews() {
    return authority.getUiViews();
  }

  @Override
  public List<String> getMenuItems() {
    return authority.getMenuItems();
  }

  @Override
  public Integer getOrder() {
    return authority.getOrder();
  }
}
