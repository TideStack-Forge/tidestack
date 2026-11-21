package com.ouroboros.appmodule;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.model.annotation.ProCodeModel;
import com.ouroboros.appmodule.model.BlankAppModule;
import com.ouroboros.appmodule.model.MenuModelAppModule;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.util.SPI;

@SuppressWarnings({"unused"})
public class ProCodeMenuAppModuleProjector implements AppModuleProvider {
  @Override
  public List<AppModule> getAppModules() {
    return buildAppModules();
  }

  List<AppModule> buildAppModules() {
    var allProCodeMenus = getAllProCodeMenus();
    var appModuleMap = new HashMap<String, AppModule>();

    // 先根据菜单创建模块
    for (var menu : allProCodeMenus) {
      appModuleMap.put(menu.getFullName(), new MenuModelAppModule(menu));
    }

    // 补充父级模块
    for (var menu : allProCodeMenus) {
      var parent = menu.getParent();
      if (ObjectUtils.isEmpty(parent)) {
        continue;
      }
      while (ObjectUtils.isNotEmpty(parent)) {
        if (!appModuleMap.containsKey(parent)) {
          appModuleMap.put(parent, new BlankAppModule(parent));
        }
        parent = parent.contains(".") ? parent.substring(0, parent.lastIndexOf(".")) : null;
      }
    }

    // 排序输出
    return appModuleMap.values()
        .stream()
        .sorted(Comparator.comparingInt(AppModule::getOrder))
        .collect(Collectors.toList());
  }

  List<MenuModel> getAllProCodeMenus() {
    return SPI.getCachedSortedServiceStream(MenuModelProvider.class)
        .map(MenuModelProvider::getAllMenus)
        .flatMap(m -> m.values().stream())
        .filter(menu -> menu.getClass().isAnnotationPresent(ProCodeModel.class))
        .collect(Collectors.toList());
  }
}
