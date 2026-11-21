package com.ouroboros.app;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.AppContext;
import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;
import com.ouroboros.util.SPI;

@Component
public class AppMetadataWebAppInfoContributor {
  final AppContext appContext;

  Map<String, Object> webAppInfo;

  public AppMetadataWebAppInfoContributor(AppContext appContext) {
    this.appContext = appContext;
    SingletonHolder.INSTANCE = this;
  }

  void init() {
    Map<String, MicroWebModel> microWebMap = SPI.getSortedServiceStream(MicroWebModelProvider.class)
        .map(MicroWebModelProvider::getAllMicroWebModel)
        .flatMap(List::stream)
        .collect(
        () -> new TreeMap<>(String::compareToIgnoreCase),
        (map, model) -> {
          String name = model.getName();
          if (StringUtils.isBlank(name)) {
            return;
          }
          map.put(name, model);
        },
        Map::putAll
    );

    appContext.getAppInfo().ifPresent(appInfo -> {
      webAppInfo = new HashMap<>();
      Optional.ofNullable(appInfo.getCustom().getLoginMicroWeb())
          .map(microWebMap::get)
          .ifPresent(model -> {
            webAppInfo.put("loginEntry", Optional.ofNullable(model.getEntry()).orElse(model.getPublicPath()));
          });
      Optional.ofNullable(appInfo.getCustom().getTopBarMicroWeb())
          .map(microWebMap::get)
          .ifPresent(model -> {
            webAppInfo.put("headerEntry", Optional.ofNullable(model.getEntry()).orElse(model.getPublicPath()));
          });
      Optional.ofNullable(appInfo.getCustom().getSideBarMicroWeb())
          .map(microWebMap::get)
          .ifPresent(model -> {
            webAppInfo.put("sidebarEntry", Optional.ofNullable(model.getEntry()).orElse(model.getPublicPath()));
          });
    });
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  public void onOuroborosModelRefreshedEvent(OuroborosContextRefreshedEvent event) {
    init();
  }

  public Map<String, Object> getWebAppInfo() {
    return Optional.ofNullable(webAppInfo)
        .orElseGet(Collections::emptyMap);
  }

  public static class SingletonHolder implements WebAppInfoContributor {
    public static AppMetadataWebAppInfoContributor INSTANCE;

    @Override
    public Map<String, Object> getWebAppInfo() {
      return Optional.ofNullable(INSTANCE)
          .map(AppMetadataWebAppInfoContributor::getWebAppInfo)
          .orElseGet(Collections::emptyMap);
    }
  }
}
