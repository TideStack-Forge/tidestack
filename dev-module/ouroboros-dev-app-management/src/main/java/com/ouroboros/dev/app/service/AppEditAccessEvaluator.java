package com.ouroboros.dev.app.service;

public interface AppEditAccessEvaluator {

  String EDIT_ACCESS_MANAGEMENT_AUTHORITY = "Apps.editAccessManagement";

  boolean canView(String appName, String userIdentity);

  boolean canEdit(String appName, String userIdentity);

  boolean isRestricted(String appName);

  boolean canManageApp(String appName, String userIdentity);

  boolean canManageEditAccess(String appName, String userIdentity);
}
