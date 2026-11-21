package com.ouroboros.documentmanagement.webapp.dto;

/**
 * Principal 类型 DTO
 */
public class PrincipalTypeDTO {
  private String name;
  private String displayName;

  public PrincipalTypeDTO() {}

  public PrincipalTypeDTO(String name, String displayName) {
    this.name = name;
    this.displayName = displayName;
  }

  public String getName() {
    return name;
  }

  public PrincipalTypeDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public PrincipalTypeDTO setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }
}
