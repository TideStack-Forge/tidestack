package com.ouroboros.menu.service;

import java.util.List;

import com.ouroboros.menu.MenuModel;

/**
 * @author liansz
 */
public interface MenuService {

  /**
   * 获取主菜单
   *
   * @return
   */
  List<? extends MenuModel> getMainMenus(String userIdentity);

  /**
   * 获取次级菜单
   *
   * @return
   */
  List<? extends MenuModel> getSecondaryMenus(String userIdentity);
}
