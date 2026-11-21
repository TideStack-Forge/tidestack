package com.ouroboros.menu.service.impl;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.vavr.Tuple2;

import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.menu.BaseMenuModel;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelCenter;
import com.ouroboros.menu.model.TreeMenuModelVo;
import com.ouroboros.menu.service.MenuService;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;
import com.ouroboros.util.Trees;

/**
 * @author liansz
 */
@Component
public class MenuServiceImpl implements MenuService {
  AppModuleIntegrator appModuleIntegrator;

  public MenuServiceImpl(ObjectProvider<AppModuleIntegrator> appModuleIntegrator) {
    this.appModuleIntegrator = appModuleIntegrator
        .getIfAvailable(() -> Collections::emptyList);
  }

  @SuppressWarnings("SimplifyStreamApiCallChains")
  @Override
  public List<? extends MenuModel> getMainMenus(String userIdentity) {
    return buildMainMenus(MenuModelCenter.getAllMenu(), getAuthorityMenuItems());
  }

  private List<String> getAuthorityMenuItems() {
    // 获取用户所有角色的所有权限的所有菜单项
    return CurrentSecurityContextHolder.findAuthentication()
        .map(authentication -> authentication.getAuthorities()
            .stream()
            .map(OuroborosGrantedAuthority::getAuthorityObject)
            .filter(Objects::nonNull)
            .flatMap(authority -> authority.getMenuItems().stream())
            .collect(Collectors.toList()))
        .orElseGet(() -> CurrentSecurityContextHolder.findIdentity()
            .map(identity -> com.ouroboros.security.authorize.AuthorizationEvaluator.standard()
                .effectiveAuthorities(identity, com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API)
                .keySet().stream()
                .map(name -> com.ouroboros.security.authority.AuthorityCenter.getAuthority(name).orElse(null))
                .filter(Objects::nonNull)
                .flatMap(authority -> authority.getMenuItems().stream())
                .collect(Collectors.toList()))
            .orElseGet(Collections::emptyList));
  }

  List<? extends MenuModel> buildMainMenus(List<MenuModel> rawMenus, List<String> authorityMenuItems) {
    var menus = getArrangedMenusByScope(rawMenus, MenuModel.MenuScope.MAIN);

    List<TreeMenuModelVo> authoredMenus = getAuthorizedMenuTreeViews(menus, authorityMenuItems);

    var treeRoots = authoredMenus
        .stream()
        .filter(menu -> ObjectUtils.isEmpty(menu.getParent()))
        .collect(Collectors.toList());
    Function<TreeMenuModelVo, List<TreeMenuModelVo>> childrenFinder = menu -> authoredMenus
        .stream()
        .filter(m -> Objects.equals(m.getParent(), menu.getFullName()))
        .collect(Collectors.toList());
    BiFunction<TreeMenuModelVo, List<TreeMenuModelVo>, TreeMenuModelVo> childrenSetter = (menu, children) -> {
      var newChildren = children
          .stream()
          .filter(ObjectUtils::isNotEmpty)
          .collect(Collectors.toList());

      // 如果没有子菜单且没有地址，则返回 null
      if (newChildren.isEmpty() && ObjectUtils.isEmpty(menu.getUrl())) {
        return null;
      }
      menu.setChildren(newChildren);
      return menu;
    };

    List<TreeMenuModelVo> tree = Trees.buildTree(treeRoots, childrenFinder, childrenSetter);

    return tree.stream()
        .filter(ObjectUtils::isNotEmpty)
        // 过滤掉没有地址或没有子菜单的菜单项
        .filter(m -> ObjectUtils.isNotEmpty(m.getUrl()) || ObjectUtils.isNotEmpty(m.getChildren()))
        .collect(Collectors.toList());
  }

  @Override
  public List<? extends MenuModel> getSecondaryMenus(String userIdentity) {
    return buildSecondaryMenus(loadLegacySecondaryMenus(), MenuModelCenter.getAllMenu(), getAuthorityMenuItems());
  }

  List<MenuModel> buildSecondaryMenus(List<MenuModel> legacyMenus, List<MenuModel> rawMenus, List<String> authorityMenuItems) {
    var scopedMenus = getAuthorizedMenus(
        getArrangedMenusByScope(rawMenus, MenuModel.MenuScope.SECONDARY),
        authorityMenuItems);
    return mergeSecondaryMenus(legacyMenus, scopedMenus);
  }

  private List<MenuModel> getArrangedMenusByScope(List<MenuModel> rawMenus, MenuModel.MenuScope scope) {
    return appModuleIntegrator.rearrangeMenus(rawMenus)
        .stream()
        .filter(menu -> isInScope(menu, scope))
        .collect(Collectors.toList());
  }

  private static boolean isInScope(MenuModel menu, MenuModel.MenuScope scope) {
    return menu != null && (menu.getScope() == scope || menu.getScope() == MenuModel.MenuScope.ALL);
  }

  private static List<MenuModel> getAuthorizedMenus(List<MenuModel> menus, List<String> authorityMenuItems) {
    return menus.stream()
        .filter(menu -> authorityMenuItems.stream().anyMatch(menuName -> isAuthorizedMenu(menuName, menu)))
        .collect(Collectors.toList());
  }

  private static List<TreeMenuModelVo> getAuthorizedMenuTreeViews(List<MenuModel> menus,
      List<String> authorityMenuItems) {
    var authorizedMenus = getAuthorizedMenus(menus, authorityMenuItems);
    var authorizedNames = authorizedMenus.stream().map(MenuModel::getFullName).collect(Collectors.toSet());
    var visibleNames = new LinkedHashSet<>(authorizedNames);
    var menusByName = menus.stream().collect(Collectors.toMap(MenuModel::getFullName,
        Function.identity(), (first, second) -> first));

    // 模块重排后，实际父目录不一定是菜单名称的前缀；补齐导航路径但不增加页面权限。
    for (var menu : authorizedMenus) {
      var parent = menusByName.get(menu.getParent());
      while (parent != null && visibleNames.add(parent.getFullName())) {
        parent = menusByName.get(parent.getParent());
      }
    }

    return menus.stream()
        .filter(menu -> visibleNames.contains(menu.getFullName()))
        .map(menu -> new TreeMenuModelVo(menu, authorizedNames.contains(menu.getFullName())))
        .collect(Collectors.toList());
  }

  private static boolean isAuthorizedMenu(String authorityMenuItem, MenuModel menu) {
    if (authorityMenuItem == null || menu == null || menu.getFullName() == null) {
      return false;
    }
    return authorityMenuItem.equalsIgnoreCase(menu.getFullName())
        || authorityMenuItem.toLowerCase(Locale.ROOT).startsWith(menu.getFullName().toLowerCase(Locale.ROOT) + ".");
  }

  private static List<MenuModel> mergeSecondaryMenus(List<MenuModel> legacyMenus, List<MenuModel> scopedMenus) {
    var result = new ArrayList<MenuModel>();
    var menuNames = new LinkedHashSet<String>();
    appendUniqueMenus(result, menuNames, legacyMenus);
    appendUniqueMenus(result, menuNames, scopedMenus);
    return result;
  }

  private static void appendUniqueMenus(List<MenuModel> result, Set<String> menuNames, List<? extends MenuModel> menus) {
    for (var menu : menus) {
      if (menu == null) {
        continue;
      }
      var menuName = menu.getFullName() == null ? "" : menu.getFullName().toLowerCase(Locale.ROOT);
      if (menuNames.add(menuName)) {
        result.add(menu);
      }
    }
  }

  private static List<MenuModel> loadLegacySecondaryMenus() {
    String path = "META-INF/ouroboros/menu-secondary";
    Pattern pattern = Pattern.compile(".*\\.json");
    return ResourceUtil.getResourceNameAndContentListFromDirectory(path, pattern)
        .stream()
        .map(Tuple2::_2)
        .map(jsonContent -> Json.toBean(BaseMenuModel.class, jsonContent))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
