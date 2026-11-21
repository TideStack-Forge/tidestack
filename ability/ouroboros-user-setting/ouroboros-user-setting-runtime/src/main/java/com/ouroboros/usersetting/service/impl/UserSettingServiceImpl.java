package com.ouroboros.usersetting.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.model.AppInfo;
import com.ouroboros.usersetting.model.UserSetting;
import com.ouroboros.usersetting.model.UserSettingEntity;
import com.ouroboros.usersetting.service.UserSettingProvider;
import com.ouroboros.usersetting.service.UserSettingService;
import com.ouroboros.util.Dates;
import com.ouroboros.util.Maps;

@Service
public class UserSettingServiceImpl implements UserSettingService, UserSettingProvider {
  private final static Logger logger = LoggerFactory.getLogger(UserSettingServiceImpl.class);
  private final AppContext appContext;

  @Autowired
  private TypedDataModel<String, UserSettingEntity> dataModel;

  public UserSettingServiceImpl(AppContext appContext) {
    this.appContext = appContext;
  }

  private TypedDataModel<String, UserSettingEntity> getDataModel() {
    return dataModel;
  }

  @Override
  public Try<Void> saveSetting(String userId, String clientType, String settingName, String setting, Boolean isAutoLoad) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userId)
        .put("appName", appName)
        .put("clientType", clientType)
        .put("settingName", settingName)
        .build();
    var queryResult = Try.of(this::getDataModel)
        .mapTry(dataModel -> dataModel
            .query(Collections.singletonMap("where", where))
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("查询用户设置失败", e))
        .getOrElse(Collections::emptyList);
    var entity = new UserSettingEntity();
    entity.setUserId(userId)
        .setAppName(appName)
        .setClientType(clientType)
        .setSettingName(settingName)
        .setSettingValue(setting)
        .setIsAutoLoad(isAutoLoad);
    if (queryResult.isEmpty()) {
      entity.setCreatedAt(Dates.now());
      return Try.of(this::getDataModel)
          .map(dataModel -> dataModel.insert(entity))
          .onFailure(e -> logger.warn("保存用户设置失败", e))
          .map(e -> null);
    }
    entity.setId(queryResult.get(0).getId())
        .setUpdatedAt(Dates.now());
    return Try.of(this::getDataModel)
        .map(dataModel -> dataModel.update(entity.getId(), entity))
        .onFailure(e -> logger.warn("保存用户设置失败", e))
        .map(e -> null);
  }

  @Override
  public Try<Void> saveSetting(String userId, String clientType, String settingName, String setting) {
    return saveSetting(userId, clientType, settingName, setting, true);
  }

  @Override
  public Optional<? extends UserSetting> getSetting(String userId, String clientType, String settingName) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userId)
        .put("appName", appName)
        .put("settingName", settingName)
        .build();
    var queryResult = Try.of(this::getDataModel)
        .mapTry(dataModel -> dataModel
            .query(Collections.singletonMap("where", where))
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("查询用户设置失败", e))
        .getOrElse(Collections::emptyList);

    var bestMatchSettings = filterAndSortByBestMatching(queryResult, clientType);

    if (bestMatchSettings.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(bestMatchSettings.get(0).getUserSetting());
  }

/**
 * 根据应用名称和客户端类型过滤并排序用户设置列表
 * 过滤规则：设置中的应用名称和客户端类型可以为空，或者与输入参数匹配
 * 排序规则：根据应用名称和客户端类型的匹配程度进行排序，完全匹配的应用名称得1分，完全匹配的客户端类型得2分
 *
 * @param settings 用户设置实体列表
 * @param clientType 客户端类型
 * @return 过滤和排序后的用户设置列表
 */
  private List<UserSettingEntity> filterAndSortByBestMatching(List<UserSettingEntity> settings, String clientType) {
    // 使用Stream API对设置列表进行处理
    return settings.stream()
        // 过滤设置：客户端类型为空或匹配
        .filter(setting -> StringUtils.isEmpty(setting.getClientType()) || setting.getClientType().equals(clientType))
        // 根据匹配程度进行排序
        .sorted(Comparator.comparingLong(setting -> {
          // 判断客户端类型是否匹配（不区分大小写）
          var isSameClient = StringUtils.equalsIgnoreCase(setting.getClientType(), clientType);
          return isSameClient ? 2 : 1;
        }))
        // 将处理后的结果收集为List返回
        .collect(Collectors.toList());
  }

  @Override
  public List<? extends UserSetting> getAllSettings(String userId, String clientType) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userId)
        .put("appName", appName)
        .build();
    var queryResult = Try.of(() -> getDataModel())
        .mapTry(dataModel -> dataModel
            .query(Collections.singletonMap("where", where))
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("查询用户设置失败", e))
        .getOrElse(Collections::emptyList);
    var bestMatchSettings = filterAndSortByBestMatching(queryResult, clientType);
    return bestMatchSettings.stream()
        .collect(Collectors.groupingBy(UserSettingEntity::getSettingName))
        .values()
        .stream()
        .map(settings -> settings.get(0).getUserSetting())
        .collect(Collectors.toList());
  }

  @Override
  public Try<Void> deleteSetting(String userId, String settingName) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userId)
        .put("appName", appName)
        .put("settingName", settingName)
        .build();
    return Try
        .of(() -> {
          getDataModel().delete(where)
              .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
          return null;
        })
        .onFailure(e -> logger.warn("删除用户设置失败", e))
        .map(e -> null);
  }

  @Override
  public Try<Void> deleteSetting(String userId, String clientType, String settingName) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userId)
        .put("appName", appName)
        .put("clientType", clientType)
        .put("settingName", settingName)
        .build();
    return Try.of(this::getDataModel)
        .mapTry(dataModel -> dataModel.delete(where)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("删除用户设置失败", e))
        .map(e -> null);
  }

  @Override
  public Try<Void> deleteAllSettings(String userId) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userId)
        .put("appName", appName)
        .build();
    return Try.of(this::getDataModel)
        .mapTry(dataModel -> dataModel.delete(where)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("删除用户全部设置失败", e))
        .map(e -> null);
  }

  @Override
  public Try<Void> deleteAllSettings(Collection<String> userIds) {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("userId", userIds.stream().collect(Collectors.toList()))
        .put("appName", appName)
        .build();

    return Try.of(this::getDataModel)
        .mapTry(dataModel -> dataModel.delete(where)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("删除指定全部用户设置失败", e))
        .map(e -> null);
  }

  @Override
  public Try<Void> deleteAllSettings() {
    var appName = getAppName();
    var where = Maps.<String, Object>builder()
        .put("appName", appName)
        .build();
    return Try.of(() -> getDataModel())
        .mapTry(dataModel -> dataModel.delete(where)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onFailure(e -> logger.warn("删除全部用户设置失败", e))
        .map(e -> null);
  }


  private String getAppName() {
    return appContext.getAppInfo().map(AppInfo::getName).orElse("");
  }
}
