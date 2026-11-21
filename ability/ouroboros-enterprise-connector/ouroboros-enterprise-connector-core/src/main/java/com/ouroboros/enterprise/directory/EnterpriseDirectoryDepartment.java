package com.ouroboros.enterprise.directory;

/** Stable provider department identity and its parent identity. */
public record EnterpriseDirectoryDepartment(
    String externalId,
    String parentExternalId,
    String name) {
  public EnterpriseDirectoryDepartment {
    if (externalId == null || externalId.isBlank()) {
      throw new IllegalArgumentException("外部部门 ID 不能为空");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("外部部门名称不能为空");
    }
  }
}
