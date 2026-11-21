package com.ouroboros.usersetting.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.usersetting.model.UserSetting;

public interface UserSettingService {
  Try<Void> saveSetting(String userId, String clientType, String settingName, String setting, Boolean isAutoLoad);
  Try<Void> saveSetting(String userId, String clientType, String settingName, String setting);
  Optional<? extends UserSetting> getSetting(String userId, String clientType, String settingName);
  List<? extends UserSetting> getAllSettings(String userId, String clientType);
  Try<Void> deleteSetting(String userId, String settingName);
  Try<Void> deleteSetting(String userId, String clientType, String settingName);
  Try<Void> deleteAllSettings(String userId);
  Try<Void> deleteAllSettings(Collection<String> userIds);
  Try<Void> deleteAllSettings();
}
