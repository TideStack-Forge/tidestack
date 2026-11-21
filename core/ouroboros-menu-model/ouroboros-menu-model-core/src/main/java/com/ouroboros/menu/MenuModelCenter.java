package com.ouroboros.menu;

import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public class MenuModelCenter {

  /**
   * 获取全部菜单
   *
   * @return
   */
  public static List<MenuModel> getAllMenu() {
    return SPI.getCachedSortedServiceStream(MenuModelProvider.class)
        .flatMap(provider -> provider.getAllMenus().entrySet().stream())
        .collect(() -> new TreeMap<String, MenuModel>(String::compareToIgnoreCase),
            (map, item) -> map.put(item.getKey(), item.getValue()), TreeMap::putAll)
        .values().stream()
        .collect(Collectors.collectingAndThen(Collectors.toList(), MenuModelCenter::mergeAndSort));
  }

  public static <T extends MenuModel> List<T> mergeAndSort(List<T> menus) {
    return menus.stream()
        .collect(() -> new TreeMap<String, T>(String::compareToIgnoreCase),
            (map, menu) -> map.put(menu.getFullName(), menu), TreeMap::putAll)
        .values().stream()
        .sorted(Comparator.comparingInt(MenuModelCenter::orderOf))
        .collect(Collectors.toList());
  }

  private static int orderOf(MenuModel menu) {
    return menu.getOrder() == null ? 0 : menu.getOrder();
  }
}
