package com.ouroboros.appmodule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

@SuppressWarnings({"unused"})
@JsonDeserialize(as = BaseAppModule.class)
public interface AppModule {
  String getFullName();

  default String getParent() {
    if (ObjectUtils.isEmpty(getFullName())) {
      return null;
    }
    int lastIndexOfDot = getFullName().lastIndexOf(".");
    return lastIndexOfDot == -1 ? null : getFullName().substring(0, lastIndexOfDot);
  }

  default String getName() {
    if (ObjectUtils.isEmpty(getFullName())) {
      return null;
    }
    int lastIndexOfDot = getFullName().lastIndexOf(".");
    return lastIndexOfDot == -1 ? getFullName() : getFullName().substring(lastIndexOfDot + 1);
  }

  String getTitle();

  String getDescription();

  String getIcon();

  Integer getOrder();

  String getMenu();

  List<String> getSubMenus();

  List<String> getAuthorities();

  default Boolean isSameModule(AppModule another) {
    if (another == null) {
      return false;
    }
    if (!Objects.equals(getFullName(), another.getFullName())) {
      return false;
    }
    if (!Objects.equals(getParent(), another.getParent())) {
      return false;
    }
    if (!Objects.equals(getName(), another.getName())) {
      return false;
    }
    if (!Objects.equals(getTitle(), another.getTitle())) {
      return false;
    }
    if (!Objects.equals(getDescription(), another.getDescription())) {
      return false;
    }
    if (!Objects.equals(getIcon(), another.getIcon())) {
      return false;
    }
    // 忽略子菜单和权限
    return Objects.equals(getOrder(), another.getOrder());
  }

  default Optional<AppModuleDiff> diff(AppModule originalModule) {
    if (isSameModule(originalModule)) {
      return Optional.empty();
    }
    return Optional.of(new AppModuleDiff.Diff(originalModule, this));
  }

  /**
   * 合并两个模块，如果两个模块有相同的属性，以第二个模块为准
   */
  class MergedAppModule implements AppModule {
    private final AppModule moduleA;
    private final AppModule moduleB;

    public MergedAppModule(AppModule moduleA, AppModule moduleB) {
      this.moduleA = moduleA;
      this.moduleB = moduleB;
    }

    @Override
    public String getFullName() {
      if (ObjectUtils.isEmpty(this.moduleB.getFullName())) {
        return this.moduleA.getFullName();
      }
      return this.moduleB.getFullName();
    }

    @Override
    public String getParent() {
      if (ObjectUtils.isEmpty(this.moduleB.getParent())) {
        return this.moduleA.getParent();
      }
      return this.moduleB.getParent();
    }

    @Override
    public String getName() {
      if (ObjectUtils.isEmpty(this.moduleB.getName())) {
        return this.moduleA.getName();
      }
      return this.moduleB.getName();
    }

    @Override
    public String getTitle() {
      if (ObjectUtils.isEmpty(this.moduleB.getTitle())) {
        return this.moduleA.getTitle();
      }
      return this.moduleB.getTitle();
    }

    @Override
    public String getDescription() {
      if (ObjectUtils.isEmpty(this.moduleB.getDescription())) {
        return this.moduleA.getDescription();
      }
      return this.moduleB.getDescription();
    }

    @Override
    public String getIcon() {
      if (ObjectUtils.isEmpty(this.moduleB.getIcon())) {
        return this.moduleA.getIcon();
      }
      return this.moduleB.getIcon();
    }

    @Override
    public Integer getOrder() {
      if (ObjectUtils.isEmpty(this.moduleB.getOrder())) {
        return this.moduleA.getOrder();
      }
      return this.moduleB.getOrder();
    }

    @Override
    public String getMenu() {
      if (ObjectUtils.isEmpty(this.moduleB.getMenu())) {
        return this.moduleA.getMenu();
      }
      return this.moduleB.getMenu();
    }

    @Override
    public List<String> getSubMenus() {
      var subMenusA = this.moduleA.getSubMenus();
      var subMenusB = this.moduleB.getSubMenus();
      if (ObjectUtils.isEmpty(subMenusB)) {
        return subMenusA;
      }
      if (ObjectUtils.isEmpty(subMenusA)) {
        return subMenusB;
      }
      return Stream.concat(subMenusA.stream(), subMenusB.stream())
          .distinct()
          .collect(Collectors.toList());
    }

    @Override
    public List<String> getAuthorities() {
      var authoritiesA = this.moduleA.getAuthorities();
      var authoritiesB = this.moduleB.getAuthorities();
      if (ObjectUtils.isEmpty(authoritiesB)) {
        return authoritiesA;
      }
      if (ObjectUtils.isEmpty(authoritiesA)) {
        return authoritiesB;
      }
      return Stream.concat(authoritiesA.stream(), authoritiesB.stream())
          .distinct()
          .collect(Collectors.toList());
    }
  }

  /**
   * 树形结构位置与模块的位置关联的菜单
   */
  class AppModuleRelatedMenu implements MenuModel {
    private final AppModule appModule;
    private final MenuModel menuModel;

    public AppModuleRelatedMenu(AppModule appModule, MenuModel menuModel) {
      this.appModule = appModule;
      this.menuModel = menuModel;
    }

    @Override
    public String getFullName() {
      return Optional.ofNullable(menuModel)
          .map(MenuModel::getFullName)
          .orElseGet(appModule::getFullName);
    }

    @Override
    public String getParent() {
      return appModule.getParent();
    }

    @Override
    public String getUrl() {
      return Optional.ofNullable(menuModel)
          .map(MenuModel::getUrl)
          .orElse(null);
    }

    @Override
    public String getIcon() {
      return appModule.getIcon();
    }

    @Override
    public String getTitle() {
      return appModule.getTitle();
    }

    @Override
    public MenuModelShowModeEnum getShowMode() {
      return Optional.ofNullable(menuModel)
          .map(MenuModel::getShowMode)
          .orElse(MenuModelShowModeEnum.TAB);
    }

    @Override
    public Integer getOrder() {
      return appModule.getOrder();
    }
  }

  /**
   * 树形结构位置与模块的位置关联的子菜单
   */
  class AppModuleRelatedSubMenu implements MenuModel {
    private final AppModule appModule;
    private final MenuModel menuModel;

    public AppModuleRelatedSubMenu(AppModule appModule, MenuModel menuModel) {
      this.appModule = appModule;
      this.menuModel = menuModel;
    }

    @Override
    public String getFullName() {
      return menuModel.getFullName();
    }

    @Override
    public String getParent() {
      return appModule.getFullName();
    }

    @Override
    public String getUrl() {
      return menuModel.getUrl();
    }

    @Override
    public String getIcon() {
      return menuModel.getIcon();
    }

    @Override
    public String getTitle() {
      return menuModel.getTitle();
    }

    @Override
    public MenuModelShowModeEnum getShowMode() {
      return menuModel.getShowMode();
    }

    @Override
    public Integer getOrder() {
      return menuModel.getOrder();
    }
  }

  /**
   * 树形结构位置与模块的位置关联的权限
   */
  class AppModuleRelatedAuthority implements Authority {
    private final AppModule appModule;
    private final Authority authority;

    public AppModuleRelatedAuthority(AppModule appModule, Authority authority) {
      this.appModule = appModule;
      this.authority = authority;
    }

    @Override
    public String getParent() {
      if (Objects.equals(appModule.getFullName(), authority.getFullName())) {
        return appModule.getParent();
      }
      return appModule.getFullName();
    }

    @Override
    public String getFullName() {
      return authority.getFullName();
    }

    @Override
    public String getTitle() {
      return authority.getTitle();
    }

    @Override
    public Integer getOrder() {
      return authority.getOrder();
    }

    @Override
    public AccessControlDefine getAccessControlDefine() {
      return authority.getAccessControlDefine();
    }

    @Override
    public List<String> getApis() {
      return authority.getApis();
    }

    @Override
    public List<String> getUiViews() {
      return authority.getUiViews();
    }

    @Override
    public List<String> getMenuItems() {
      return authority.getMenuItems();
    }
  }


  /**
   * 用于基于模块的菜单模型
   */
  class AppModuleMenuModel implements MenuModel {
    private final AppModule appModule;

    public AppModuleMenuModel(AppModule appModule) {
      this.appModule = appModule;
    }

    @Override
    public String getUrl() {
      return null;
    }

    @Override
    public String getFullName() {
      return appModule.getFullName();
    }

    @Override
    public String getIcon() {
      return appModule.getIcon();
    }

    @Override
    public String getTitle() {
      return appModule.getTitle();
    }

    @Override
    public MenuModelShowModeEnum getShowMode() {
      return null;
    }

    @Override
    public Integer getOrder() {
      return appModule.getOrder();
    }
  }
}
