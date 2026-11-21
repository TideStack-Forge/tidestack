package com.ouroboros.dev.app.model;

import java.time.LocalDateTime;

import com.ouroboros.model.AppInfo;

@SuppressWarnings("unused")
public class AppInfoWithMeta {
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  AppInfo appInfo;
  boolean canManageEditAccess;

  public AppInfoWithMeta(AppInfo appInfo, LocalDateTime createdAt, LocalDateTime updatedAt) {
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.appInfo = appInfo;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public AppInfo getAppInfo() {
    return appInfo;
  }

  public void setAppInfo(AppInfo appInfo) {
    this.appInfo = appInfo;
  }

  public boolean getCanManageEditAccess() {
    return canManageEditAccess;
  }

  public void setCanManageEditAccess(boolean canManageEditAccess) {
    this.canManageEditAccess = canManageEditAccess;
  }
}
