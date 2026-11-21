package com.ouroboros.app;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.metadata.ConfigScope;

// 为 /sys/web-app-info 提供数据
@Service
public class AppConfigWebAppInfoContributor {
  ConfigurationService configurationService;

  public AppConfigWebAppInfoContributor(ConfigurationService configurationService) {
    this.configurationService = configurationService;
    SingletonHolder.INSTANCE = this;
  }

  public Map<String, Object> getWebAppInfo() {
    var infoMap = new HashMap<String, Object>();
    configurationService.get(String.class, "appName")
        .ifPresent(appName -> infoMap.put("appName", appName));
    configurationService.get(String.class, "logo")
        .ifPresent(logo -> infoMap.put("logo", logo));
    configurationService.get(String.class, "homePage")
        .ifPresent(homePage -> infoMap.put("homePage", homePage));
    configurationService.get(String.class, "defaultTheme")
        .ifPresent(defaultTheme -> infoMap.put("defaultTheme", defaultTheme));

    // 增加前端配置
    infoMap.put("config", configurationService.getValues(ConfigScope.FRONTEND));
    return infoMap;
  }

  public static class SingletonHolder implements WebAppInfoContributor {
    public static AppConfigWebAppInfoContributor INSTANCE;

    @Override
    public Map<String, Object> getWebAppInfo() {
      return Optional.ofNullable(INSTANCE)
          .map(AppConfigWebAppInfoContributor::getWebAppInfo)
          .orElseGet(Collections::emptyMap);
    }
  }
}
