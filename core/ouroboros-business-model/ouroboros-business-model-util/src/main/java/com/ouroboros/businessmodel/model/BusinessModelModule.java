package com.ouroboros.businessmodel.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.security.authority.Authority;

public class BusinessModelModule implements AppModule {
  private ModelBundle modelBundle;
  private BusinessModel businessModel;

  public BusinessModelModule(BusinessModel businessModel) {
    this.businessModel = businessModel;
  }

  public BusinessModelModule(BusinessModel businessModel, ModelBundle modelBundle) {
    this.businessModel = businessModel;
    this.modelBundle = modelBundle;
  }

  public ModelBundle buildModelBundle() {
    this.modelBundle = businessModel.buildModelBundle();
    return modelBundle;
  }

  @Override
  public String getFullName() {
    return businessModel.getFullName();
  }

  @Override
  public String getName() {
    return businessModel.getName();
  }

  @Override
  public String getTitle() {
    return businessModel.getTitle();
  }

  @Override
  public String getDescription() {
    return businessModel.getDescription();
  }

  @Override
  public String getIcon() {
    return businessModel.getIcon();
  }

  @Override
  public Integer getOrder() {
    return businessModel.getExtraProp(Integer.class, "order").orElse(0);
  }

  @Override
  public String getMenu() {
    return Optional.ofNullable(modelBundle)
        .orElseGet(this::buildModelBundle)
        .getModelMap(MenuModel.class)
        .values()
        .stream()
        .map(MenuModel::getFullName)
        .filter(getFullName()::equalsIgnoreCase)
        .findFirst()
        .orElse(null);
  }

  @Override
  public List<String> getSubMenus() {
    return Optional.ofNullable(modelBundle)
        .orElseGet(this::buildModelBundle)
        .getModelMap(MenuModel.class)
        .values()
        .stream()
        .map(MenuModel::getFullName)
        .filter(menu -> !Objects.equals(menu, getFullName()))
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getAuthorities() {
    return Optional.ofNullable(modelBundle)
        .orElseGet(this::buildModelBundle)
        .getModelMap(Authority.class)
        .values()
        .stream()
        .map(Authority::getFullName)
        .collect(Collectors.toList());
  }
}
