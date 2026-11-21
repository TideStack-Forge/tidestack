package com.ouroboros.dev.app.service;

import java.util.Optional;

import com.ouroboros.dev.app.model.AppOwnerEntity;

public interface AppOwnerService {

  Optional<AppOwnerEntity> getByAppName(String appName);

  boolean isOwner(String appName, String userIdentity);

  void assignOwner(String appName, String ownerUserIdentity, String operator);

  void renameOwner(String appName, String newAppName);

  void deleteOwner(String appName);
}
