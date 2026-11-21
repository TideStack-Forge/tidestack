package com.ouroboros.dev.app.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.app.model.DevMenuModel;
import com.ouroboros.dev.app.service.DevMenuService;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.util.Trees;

@Service
public class DevMenuServiceImpl implements DevMenuService {
  private static final Logger logger = LoggerFactory.getLogger(DevMenuServiceImpl.class);
  private static final String DEV_MENU_SUFFIX = ".dev-menu.json";

  List<DevMenuModel> mainDevMenus;
  List<DevMenuModel> secondaryDevMenus;
  Map<String, MenuModel> mainMenuMap;

  @SuppressWarnings("SimplifyStreamApiCallChains")
  public DevMenuServiceImpl() {
    this(loadMenuList());
  }

  DevMenuServiceImpl(List<DevMenuModel> menuList) {
    mainDevMenus = menuList.stream()
        .filter(menu -> menu.getScope() == DevMenuModel.MenuScope.MAIN || menu.getScope() == DevMenuModel.MenuScope.ALL)
        .filter(menu -> menu.getStage() == DevMenuModel.MenuStage.DEV || menu.getStage() == DevMenuModel.MenuStage.ALL)
        .collect(Collectors.collectingAndThen(Collectors.toList(), DevMenuServiceImpl::mergeAndSort));
    secondaryDevMenus = menuList.stream()
        .filter(menu -> menu.getScope() == DevMenuModel.MenuScope.SECONDARY || menu.getScope() == DevMenuModel.MenuScope.ALL)
        .filter(menu -> menu.getStage() == DevMenuModel.MenuStage.DEV || menu.getStage() == DevMenuModel.MenuStage.ALL)
        .collect(Collectors.collectingAndThen(Collectors.toList(), DevMenuServiceImpl::mergeAndSort));
    mainMenuMap = menuList.stream()
        .filter(menu -> menu.getStage() == DevMenuModel.MenuStage.WELCOME || menu.getStage() == DevMenuModel.MenuStage.ALL)
        .collect(() -> new TreeMap<String, MenuModel>(String::compareToIgnoreCase), (map, menu) -> map.put(menu.getFullName(), menu), TreeMap::putAll);
    SingletonHolder.INSTANCE = this;
  }

  @SuppressWarnings("SimplifyStreamApiCallChains")
  private static List<DevMenuModel> loadMenuList() {
    var spec = TypedMetadataSpec.<DevMenuModel>builder(DevMenuModel.class)
        .typedSuffix(DEV_MENU_SUFFIX)
        .jsonParser(DevMenuModel.class)
        .keyExtractor(DevMenuModel::getFullName)
        .build();
    var result = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);
    result.diagnostics().forEach(DevMenuServiceImpl::logDiagnostic);
    return List.copyOf(result.values().values());
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("开发菜单元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("开发菜单元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  @Override
  public List<DevMenuModel> getMainMenus(String appName) {
    var menuList = mainDevMenus.stream()
        .map(mm -> mm.toAppMenuModel(appName, getUserId()))
        .collect(Collectors.toList());
    // 构建菜单树
    List<DevMenuModel> menuTree = Trees
        .<DevMenuModel>simpleBuilder()
        .rootPredicate(m -> m.getParent() == null)
        .childPredicate((parent, child) -> Objects.equals(parent.getFullName(), child.getParent()))
        .childrenSetter(DevMenuModel::setChildren)
        .build(menuList);
    return menuTree;
  }

  @Override
  public List<DevMenuModel> getSecondaryMenus(String appName) {
    return secondaryDevMenus.stream()
        .map(mm -> mm.toAppMenuModel(appName, getUserId()))
        .collect(Collectors.toList());
  }

  public Map<String, MenuModel> getAllMenus() {
    return mainMenuMap;
  }

  private static List<DevMenuModel> mergeAndSort(List<DevMenuModel> menus) {
    return menus.stream()
        .collect(() -> new TreeMap<String, DevMenuModel>(String::compareToIgnoreCase),
            (map, menu) -> map.put(menu.getFullName(), menu), TreeMap::putAll)
        .values().stream()
        .sorted(Comparator.comparingInt(menu -> menu.getOrder() == null ? 0 : menu.getOrder()))
        .collect(Collectors.toList());
  }

  private String getUserId() {
    return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
  }

  public static class SingletonHolder implements MenuModelProvider {
    static DevMenuServiceImpl INSTANCE;

    @Override
    public Map<String, MenuModel> getAllMenus() {
      return Optional.ofNullable(INSTANCE)
          .map(DevMenuServiceImpl::getAllMenus)
          .orElseGet(Collections::emptyMap);
    }
  }
}
