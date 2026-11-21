package com.ouroboros.appmodule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProCodeAppModuleProvider implements AppModuleProvider {
  AppModuleProvider authorityAppModuleProvider;
  AppModuleProvider menuAppModuleProvider;

  public ProCodeAppModuleProvider() {
    authorityAppModuleProvider = new ProCodeAuthorityAppModuleProjector();
    menuAppModuleProvider = new ProCodeMenuAppModuleProjector();
  }

  @Override
  public List<AppModule> getAppModules() {
    var authorityAppModules = authorityAppModuleProvider.getAppModules()
        .stream()
        .collect(Collectors.toMap(AppModule::getFullName, a -> a, (v1, v2) -> v2));
    var menuAppModules = menuAppModuleProvider.getAppModules()
        .stream()
        .collect(Collectors.toMap(AppModule::getFullName, a -> a, (v1, v2) -> v2));
    var appModules = new ArrayList<AppModule>();
    menuAppModules.forEach((k, menuAppModule) -> {
      var authorityAppModule = authorityAppModules.get(k);
      if (authorityAppModule == null) {
        appModules.add(menuAppModule);
        return;
      }
      appModules.add(new AppModule.MergedAppModule(authorityAppModule, menuAppModule));
    });
    authorityAppModules.forEach((k, authorityAppModule) -> {
      if (menuAppModules.containsKey(k)) {
        return;
      }
      appModules.add(authorityAppModule);
    });
    return appModules.stream()
        .sorted(Comparator.comparingInt(m -> Optional.ofNullable(m.getOrder()).orElse(0)))
        .collect(Collectors.toList());
  }
}
