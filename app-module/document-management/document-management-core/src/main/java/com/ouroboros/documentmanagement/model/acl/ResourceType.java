package com.ouroboros.documentmanagement.model.acl;

/**
 * 资源类型
 */
public enum ResourceType {
  LIBRARY("library", "文档库"),
  DIRECTORY("directory", "目录"),
  DOCUMENT("document", "文档");

  private final String code;
  private final String displayName;

  ResourceType(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }
}
