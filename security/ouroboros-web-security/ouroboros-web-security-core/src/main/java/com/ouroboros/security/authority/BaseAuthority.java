package com.ouroboros.security.authority;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

public class BaseAuthority implements Authority {

  /**
   * 全名称（唯一标识）
   */
  String fullName;

  /**
   * 标题
   */
  String title;
  private boolean delegable;

  /**
   * 通行控制参数定义
   */
  AccessControlDefine accessControlDefine;

  /**
   * api列表
   */
  List<String> apis = new ArrayList<>();

  /**
   * ui视图列表
   */
  List<String> uiViews = new ArrayList<>();

  /**
   * 菜单项列表
   */
  List<String> menuItems = new ArrayList<>();

  public BaseAuthority() {
  }

  public BaseAuthority(Authority authority) {
    this.fullName = authority.getFullName();
    this.title = authority.getTitle();
    this.delegable = authority.isDelegable();
    this.accessControlDefine = authority.getAccessControlDefine();
    this.apis = new ArrayList<>(authority.getApis());
    this.uiViews = new ArrayList<>(authority.getUiViews());
    this.menuItems = new ArrayList<>(authority.getMenuItems());
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public boolean isDelegable() { return delegable; }

  public void setDelegable(boolean value) { delegable = value; }

  @Override
  public AccessControlDefine getAccessControlDefine() {
    return Optional.ofNullable(accessControlDefine).orElseGet(AccessControlDefine::empty);
  }

  public void setAccessControlDefine(AccessControlDefine accessControlDefine) {
    this.accessControlDefine = accessControlDefine;
  }

  @Override
  public List<String> getApis() {
    return apis;
  }

  public void setApis(List<String> apis) {
    this.apis = apis;
  }

  @Override
  public List<String> getUiViews() {
    return uiViews;
  }

  public void setUiViews(List<String> uiViews) {
    this.uiViews = uiViews;
  }

  @Override
  public List<String> getMenuItems() {
    return menuItems;
  }

  public void setMenuItems(List<String> menuItems) {
    this.menuItems = menuItems;
  }
}
