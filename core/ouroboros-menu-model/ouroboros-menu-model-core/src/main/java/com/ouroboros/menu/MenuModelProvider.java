package com.ouroboros.menu;

import java.util.Map;

/**
 * @author liansz
 */
public interface MenuModelProvider {

  /**
   * 获取全部菜单
   *
   * @return
   */
  Map<String, MenuModel> getAllMenus();
}
