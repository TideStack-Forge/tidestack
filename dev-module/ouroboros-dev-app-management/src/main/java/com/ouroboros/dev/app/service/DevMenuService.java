package com.ouroboros.dev.app.service;

import java.util.List;

import com.ouroboros.menu.MenuModel;

/**
 * 开发菜单服务接口
 * 该接口定义了获取菜单相关的方法，包括主菜单和二级菜单
 */
public interface DevMenuService {

  /**
   * 根据应用名称获取主菜单列表
   * @param appName 应用名称
   * @return 返回MenuModel的子类列表，表示主菜单
   */
  List<? extends MenuModel> getMainMenus(String appName);

  /**
   * 根据应用名称获取二级菜单列表
   * @param appName 应用名称
   * @return 返回MenuModel的子类列表，表示二级菜单
   */
  List<? extends MenuModel> getSecondaryMenus(String appName);
}
