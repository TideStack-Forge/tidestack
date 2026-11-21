package com.ouroboros.businesslog;

public enum BusinessOperationTemplateSource {
  DATABASE("数据库"),
  FILE("工作区文件"),
  JAR("Jar 内置");

  private final String label;

  BusinessOperationTemplateSource(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
