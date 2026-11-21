package com.ouroboros.usersetting.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.usersetting.model.UserSetting;


/**
 * 用户设置提供者接口
 * 该接口定义了获取用户设置的方法，用于获取单个设置或所有设置
 *
 * Provider 提供分层加载机制，可在后续增加默认设置 Provider，可在用户尚未保存设置时提供默认设置
 */
public interface UserSettingProvider {
  /**
   * 获取指定的用户设置
   * @param userId 用户ID，用于标识具体的用户
   * @param clientType 客户端类型，用于区分不同的客户端
   * @param settingName 设置项名称，用于标识具体的设置
   * @return 返回一个Optional包装的UserSetting对象，如果设置不存在则返回空Optional
   */
  Optional<? extends UserSetting> getSetting(String userId, String clientType, String settingName);
  /**
   * 获取用户的所有设置
   * @param userId 用户ID，用于标识具体的用户
   * @param clientType 客户端类型，用于区分不同的客户端
   * @return 返回一个UserSetting列表，包含用户的所有设置
   */
  List<? extends UserSetting> getAllSettings(String userId, String clientType);
}
