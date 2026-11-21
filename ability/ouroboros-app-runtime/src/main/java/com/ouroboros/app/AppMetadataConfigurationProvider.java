package com.ouroboros.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.AppContext;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;

@Component
public class AppMetadataConfigurationProvider {

  private final AppContext appContext;

  public AppMetadataConfigurationProvider(AppContext appContext) {
    this.appContext = appContext;
    Proxy.provider = this;
  }

  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    return appContext.getAppInfo().map(appInfo -> {
      List<ConfigurationMetadata> list = new ArrayList<>();
      var defaultThemeConfiguration = new ConfigurationMetadata();
      defaultThemeConfiguration.setKey("defaultTheme");
      defaultThemeConfiguration.setDefaultValue(appInfo.getDefaultTheme());
      defaultThemeConfiguration.setModifiable(true);
      defaultThemeConfiguration.setNullable(false);
      defaultThemeConfiguration.setValueType(ConfigValueType.STRING);

      var appNameConfiguration = new ConfigurationMetadata();
      appNameConfiguration.setKey("appName");
      appNameConfiguration.setModifiable(true);
      appNameConfiguration.setNullable(false);
      appNameConfiguration.setValueType(ConfigValueType.STRING);
      appNameConfiguration.setDefaultValue(appInfo.getTitle());
      list.add(defaultThemeConfiguration);
      list.add(appNameConfiguration);

      var logo = appInfo.getLogo();
      var logoConfiguration = new ConfigurationMetadata();
      logoConfiguration.setKey("logo");
      logoConfiguration.setModifiable(true);
      logoConfiguration.setNullable(true);
      logoConfiguration.setValueType(ConfigValueType.STRING);
      logoConfiguration.setDefaultValue(ObjectUtils.isNotEmpty(logo) ? ("/api/app-metadata/" + logo) : null);
      list.add(logoConfiguration);

      var homePage = appInfo.getHomePage();
      var homePageConfiguration = new ConfigurationMetadata();
      homePageConfiguration.setKey("homePage");
      homePageConfiguration.setLabel("应用首页");
      homePageConfiguration.setDescription("用户登录后打开的第一个页面，默认会打开第一个菜单");
      homePageConfiguration.setModifiable(true);
      homePageConfiguration.setNullable(true);
      homePageConfiguration.setValueType(ConfigValueType.STRING);
      homePageConfiguration.setDefaultValue(ObjectUtils.isEmpty(homePage) ? null : homePage);
      list.add(homePageConfiguration);
      return list;
    }).orElseGet(Collections::emptyList);
  }

  @Priority(1)
  public static class Proxy implements ConfigurationMetadataProvider {
    private static AppMetadataConfigurationProvider provider;

    @Override
    public List<ConfigurationMetadata> getAllConfigurationMetadata() {
      return Optional.ofNullable(provider)
          .map(AppMetadataConfigurationProvider::getAllConfigurationMetadata)
          .orElseGet(Collections::emptyList);
    }
  }
}
