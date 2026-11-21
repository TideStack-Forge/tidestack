package com.ouroboros.dev.app.service;

import java.util.List;

import com.ouroboros.dev.app.model.AppEditAccessConfigRequest;
import com.ouroboros.dev.app.model.AppEditScopeEntity;

public interface AppEditScopeService {

  List<AppEditScopeEntity> getByAppName(String appName);

  boolean isRestricted(String appName);

  void replaceScopes(String appName, AppEditAccessConfigRequest request, String operator);

  void renameScopes(String appName, String newAppName);

  void deleteScopes(String appName);
}
