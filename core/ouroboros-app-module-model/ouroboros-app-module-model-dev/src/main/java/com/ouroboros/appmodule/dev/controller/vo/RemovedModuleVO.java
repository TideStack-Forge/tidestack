package com.ouroboros.appmodule.dev.controller.vo;

import com.ouroboros.appmodule.AppModule;

public class RemovedModuleVO {
  private final String fullName;
  private final String title;
  private final String icon;
  private final String description;

  public RemovedModuleVO(AppModule appModule) {
    this.fullName = appModule.getFullName();
    this.title = appModule.getTitle();
    this.icon = appModule.getIcon();
    this.description = appModule.getDescription();
  }

  public String getFullName() {
    return fullName;
  }

  public String getTitle() {
    return title;
  }

  public String getIcon() {
    return icon;
  }

  public String getDescription() {
    return description;
  }
}
