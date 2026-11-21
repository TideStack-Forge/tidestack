package com.ouroboros.organizationstructure.enums;

/**
 * @author liansz
 */
public enum OrganizationNodeType {

  COMPANY("company"),

  DEPARTMENT("department"),

  POSITION("position");

  private final String type;

  OrganizationNodeType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
