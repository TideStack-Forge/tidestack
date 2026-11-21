package com.ouroboros.appmodule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY
)
public class BaseAppModule implements AppModule {

  private String fullName;
  private String parent;
  private String title;
  private String description;
  private String icon;
  private Integer order;
  private String menu;
  private List<String> subMenus;
  private List<String> authorities;

  @Override
  public String getFullName() {
    return fullName;
  }

  @Override
  public String getParent() {
    return parent != null ? parent : AppModule.super.getParent();
  }

  @Override
  @JsonIgnore
  public String getName() {
    return AppModule.super.getName();
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

  public BaseAppModule setFullName(String fullName) {
    this.fullName = fullName;
    return this;
  }

  public BaseAppModule setParent(String parent) {
    this.parent = parent;
    return this;
  }

  public BaseAppModule setTitle(String title) {
    this.title = title;
    return this;
  }

  public BaseAppModule setDescription(String description) {
    this.description = description;
    return this;
  }

  public BaseAppModule setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public BaseAppModule setOrder(Integer order) {
    this.order = order;
    return this;
  }

  public BaseAppModule setMenu(String menu) {
    this.menu = menu;
    return this;
  }

  public BaseAppModule setSubMenus(List<String> subMenus) {
    this.subMenus = subMenus;
    return this;
  }

  public BaseAppModule setAuthorities(List<String> authorities) {
    this.authorities = authorities;
    return this;
  }
}
