package com.ouroboros.businessmodel.model;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.model.annotation.LowCodeModel;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;

@LowCodeModel
@SuppressWarnings("unused")
public class ModuleMenu implements MenuModel {
  final static String DEFAULT_MENU_ICON = "fas fa-cubes";
  private String fullName;
  private String title;
  private String icon;
  private String url;
  private int order;

  @Override
  public String getUrl() {
    return StringUtils.isEmpty(url) ? "" : url;
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  @Override
  public String getName() {
    return StringUtils.isEmpty(fullName) ? "" : fullName.substring(fullName.lastIndexOf(".") + 1);
  }

  @Override
  public String getIcon() {
    return StringUtils.isEmpty(icon) ? DEFAULT_MENU_ICON : icon;
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public MenuModelShowModeEnum getShowMode() {
    return MenuModelShowModeEnum.TAB;
  }

  @Override
  public Integer getOrder() {
    return order;
  }
}
