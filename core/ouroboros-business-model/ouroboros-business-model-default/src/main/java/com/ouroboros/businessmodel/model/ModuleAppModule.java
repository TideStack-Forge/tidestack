package com.ouroboros.businessmodel.model;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.model.annotation.LowCodeModel;

@LowCodeModel
@SuppressWarnings("unused")
@JsonDeserialize(as = ModuleAppModule.class)
public class ModuleAppModule implements AppModule {
  final static String DEFAULT_MENU_ICON = "fas fa-cubes";
  private String fullName;
  private String title;
  private String icon;
  private String url;
  private Integer order = 0;
  private String description;

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
  public String getDescription() {
    return description;
  }


  @Override
  public Integer getOrder() {
    return order;
  }

  @Override
  public String getMenu() {
    return getFullName();
  }

  @Override
  public List<String> getSubMenus() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAuthorities() {
    return Collections.singletonList(getFullName());
  }
}
