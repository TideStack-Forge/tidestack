package com.ouroboros.appmodule.model;

import java.util.Collections;
import java.util.List;

import com.ouroboros.appmodule.AppModule;

@SuppressWarnings({"unused", "ClassCanBeRecord"})
public class BlankAppModule implements AppModule {
  private final String fullName;

  public BlankAppModule(String fullName) {
    this.fullName = fullName;
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  @Override
  public String getTitle() {
    return getName();
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public String getIcon() {
    return null;
  }

  @Override
  public Integer getOrder() {
    return 0;
  }

  @Override
  public String getMenu() {
    return getFullName();
  }

  @Override
  public List<String> getSubMenus() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAuthorities() {
    return Collections.emptyList();
  }
}
