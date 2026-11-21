package com.ouroboros.documentmanagement.model.acl;

import java.util.*;

/**
 * 权限集合，表示一组权限的集合
 */
public class PermissionSet {
  private final Set<Permission> permissions;

  public PermissionSet() {
    this.permissions = EnumSet.noneOf(Permission.class);
  }

  public PermissionSet(Collection<Permission> permissions) {
    this.permissions = EnumSet.copyOf(permissions);
  }

  public PermissionSet(Permission... permissions) {
    this.permissions = EnumSet.noneOf(Permission.class);
    Collections.addAll(this.permissions, permissions);
  }

  /**
   * 添加权限
   */
  public PermissionSet add(Permission permission) {
    Set<Permission> newPermissions = EnumSet.copyOf(this.permissions);
    newPermissions.add(permission);
    return new PermissionSet(newPermissions);
  }

  /**
   * 移除权限
   */
  public PermissionSet remove(Permission permission) {
    Set<Permission> newPermissions = EnumSet.copyOf(this.permissions);
    newPermissions.remove(permission);
    return new PermissionSet(newPermissions);
  }

  /**
   * 合并权限集合（并集）
   */
  public PermissionSet merge(PermissionSet other) {
    Set<Permission> merged = EnumSet.copyOf(this.permissions);
    merged.addAll(other.permissions);
    return new PermissionSet(merged);
  }

  /**
   * 判断是否包含指定权限
   */
  public boolean has(Permission permission) {
    return permissions.contains(permission);
  }

  /**
   * 判断是否包含所有指定权限
   */
  public boolean hasAll(Permission... permissions) {
    return this.permissions.containsAll(Arrays.asList(permissions));
  }

  /**
   * 判断是否包含任一指定权限
   */
  public boolean hasAny(Permission... permissions) {
    for (Permission permission : permissions) {
      if (this.permissions.contains(permission)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 获取权限集合
   */
  public Set<Permission> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }

  /**
   * 判断是否为空
   */
  public boolean isEmpty() {
    return permissions.isEmpty();
  }

  /**
   * 创建空的权限集合
   */
  public static PermissionSet empty() {
    return new PermissionSet();
  }

  /**
   * 创建包含所有权限的权限集合
   */
  public static PermissionSet all() {
    return new PermissionSet(EnumSet.allOf(Permission.class));
  }

  /**
   * 创建查看
   */
  public static PermissionSet view() {
    return new PermissionSet(Permission.VIEW);
  }

  /**
   * 创建常用的查看权限集合（预览 + 下载）
   */
  public static PermissionSet download() {
    return new PermissionSet(Permission.VIEW, Permission.DOWNLOAD);
  }

  /**
   * 创建编辑权限集合（预览 + 下载 + 上传）
   */
  public static PermissionSet edit() {
    return new PermissionSet(Permission.VIEW, Permission.DOWNLOAD, Permission.CREATE_FILE);
  }

  /**
   * 创建删除权限集合（预览 + 下载 + 创建 + 删除）
   */
  public static PermissionSet delete() {
    return new PermissionSet(Permission.VIEW, Permission.DOWNLOAD, Permission.CREATE_FILE, Permission.CREATE_DIR, Permission.DELETE_DIR, Permission.DELETE_FILE);
  }

  /**
   * 创建管理权限集合（预览 + 下载 + 创建 + 删除 + 管理）
   */
  public static PermissionSet manage() {
    return new PermissionSet(Permission.VIEW, Permission.DOWNLOAD, Permission.CREATE_FILE, Permission.CREATE_DIR, Permission.DELETE_DIR, Permission.DELETE_FILE, Permission.MANAGE);
  }
  /**
   * 从字符串解析权限集合（逗号分隔的权限代码）
   */
  public static PermissionSet fromString(String permissionsString) {
    if (permissionsString == null || permissionsString.trim().isEmpty()) {
      return PermissionSet.empty();
    }

    Set<Permission> permissionSet = EnumSet.noneOf(Permission.class);
    String[] parts = permissionsString.split(",");
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        findPermission(trimmed).ifPresent(permissionSet::add);
      }
    }
    return new PermissionSet(permissionSet);
  }

  private static Optional<Permission> findPermission(String code) {
    for (Permission permission : Permission.values()) {
      if (permission.getCode().equalsIgnoreCase(code)) {
        return Optional.of(permission);
      }
    }
    return Optional.empty();
  }

  /**
   * 转换为字符串（逗号分隔的权限代码）
   */
  public String toPermissionString() {
    if (permissions.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Permission permission : permissions) {
      if (!first) {
        sb.append(",");
      }
      sb.append(permission.getCode());
      first = false;
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PermissionSet that = (PermissionSet) o;
    return Objects.equals(permissions, that.permissions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(permissions);
  }

  @Override
  public String toString() {
    return "PermissionSet{" + permissions + '}';
  }
}
