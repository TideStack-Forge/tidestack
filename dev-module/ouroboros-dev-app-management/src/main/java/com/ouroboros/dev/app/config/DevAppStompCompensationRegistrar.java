package com.ouroboros.dev.app.config;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppLogCacheService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.websocket.StompCompensationRegistrar;

@Component
public class DevAppStompCompensationRegistrar {
  private final StompCompensationRegistrar stompCompensationRegistrar;
  private final AppLogCacheService appLogCacheService;
  private final AppDevHelperContainerService devHelperContainerService;
  private final AppEditAccessEvaluator appEditAccessEvaluator;
  private final AppDevHelperLogService appDevHelperLogService;
  private StompCompensationRegistrar.UserTopicChannel appLogsChannel;

  public DevAppStompCompensationRegistrar(
      StompCompensationRegistrar stompCompensationRegistrar,
      AppLogCacheService appLogCacheService,
      AppDevHelperContainerService devHelperContainerService,
      AppEditAccessEvaluator appEditAccessEvaluator,
      AppDevHelperLogService appDevHelperLogService) {
    this.stompCompensationRegistrar = stompCompensationRegistrar;
    this.appLogCacheService = appLogCacheService;
    this.devHelperContainerService = devHelperContainerService;
    this.appEditAccessEvaluator = appEditAccessEvaluator;
    this.appDevHelperLogService = appDevHelperLogService;
  }

  @PostConstruct
  public void register() {
    registerAppLogs();
    registerHelperLogs();
  }

  public StompCompensationRegistrar.UserTopicChannel appLogsChannel() {
    return appLogsChannel;
  }

  private void registerAppLogs() {
    appLogsChannel = stompCompensationRegistrar
        .userTopic("/user/queue/app/{appName}/logs")
        .initialData(context -> {
          String user = requirePrincipal(context);
          String appName = requireAppName(context);
          return appLogCacheService.getCachedLogs(user, appName);
        })
        .done();
  }

  private void registerHelperLogs() {
    appDevHelperLogService.bindChannel(
        stompCompensationRegistrar
            .userTopic("/user/queue/app/{appName}/helper/logs")
            .initialData(context -> {
              String user = requirePrincipal(context);
              String appName = requireAppName(context);
              if (!appEditAccessEvaluator.canView(appName, user)) {
                throw new BusinessException("无权访问应用: " + appName);
              }
              devHelperContainerService.syncRuntimeLogs(appName);
              appDevHelperLogService.registerSubscriber(appName, user);
              return appDevHelperLogService.getReplayPayloads(appName);
            })
            .done());
  }

  private String requirePrincipal(StompCompensationRegistrar.Context context) {
    if (context == null || StringUtils.isBlank(context.getUser())) {
      throw new ValidationException("缺少 websocket principal");
    }
    return context.getUser();
  }

  private String requireAppName(StompCompensationRegistrar.Context context) {
    if (context == null || StringUtils.isBlank(context.pathVariable("appName"))) {
      throw new ValidationException("appName 不能为空");
    }
    String appName = context.pathVariable("appName");
    if (appName.contains("/") || appName.contains("\\") || appName.contains("..")) {
      throw new BusinessException("appName 非法");
    }
    return appName;
  }
}
