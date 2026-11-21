package com.ouroboros.documentmanagement.webapp.dto;

/**
 * 权限信息 DTO
 */
public class PermissionsDTO {
  // 角色权限
  private boolean canManage; // 是否可以管理（所有者或管理员）

  // 具体操作权限
  private boolean canView; // 查看权限
  private boolean canDownload; // 下载权限
  private boolean canCreateFile; // 创建文件权限
  private boolean canCreateDir; // 创建目录权限
  private boolean canRenameMove; // 重命名/移动权限
  private boolean canDeleteFile; // 删除文件权限
  private boolean canDeleteDir; // 删除目录权限

  public boolean isCanManage() {
    return canManage;
  }

  public PermissionsDTO setCanManage(boolean canManage) {
    this.canManage = canManage;
    return this;
  }

  public boolean isCanView() {
    return canView;
  }

  public PermissionsDTO setCanView(boolean canView) {
    this.canView = canView;
    return this;
  }

  public boolean isCanDownload() {
    return canDownload;
  }

  public PermissionsDTO setCanDownload(boolean canDownload) {
    this.canDownload = canDownload;
    return this;
  }

  public boolean isCanCreateFile() {
    return canCreateFile;
  }

  public PermissionsDTO setCanCreateFile(boolean canCreateFile) {
    this.canCreateFile = canCreateFile;
    return this;
  }

  public boolean isCanCreateDir() {
    return canCreateDir;
  }

  public PermissionsDTO setCanCreateDir(boolean canCreateDir) {
    this.canCreateDir = canCreateDir;
    return this;
  }

  public boolean isCanRenameMove() {
    return canRenameMove;
  }

  public PermissionsDTO setCanRenameMove(boolean canRenameMove) {
    this.canRenameMove = canRenameMove;
    return this;
  }

  public boolean isCanDeleteFile() {
    return canDeleteFile;
  }

  public PermissionsDTO setCanDeleteFile(boolean canDeleteFile) {
    this.canDeleteFile = canDeleteFile;
    return this;
  }

  public boolean isCanDeleteDir() {
    return canDeleteDir;
  }

  public PermissionsDTO setCanDeleteDir(boolean canDeleteDir) {
    this.canDeleteDir = canDeleteDir;
    return this;
  }
}