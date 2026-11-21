package com.ouroboros.appmodule.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.model.annotation.ProCodeModel;
import com.ouroboros.security.authority.Authority;

@SuppressWarnings("ClassCanBeRecord")
@ProCodeModel
public class AuthoritiesAppModule implements AppModule {
  private final List<Authority> authorities;

  public AuthoritiesAppModule(List<Authority> authorities) {
    this.authorities = authorities;
  }

  @Override
  public String getFullName() {
    return Optional.ofNullable(authorities.get(0).getParent()).orElse(authorities.get(0).getFullName());
  }

  @Override
  public String getTitle() {
    return authorities.stream().filter(a -> a.getFullName().equals(getFullName())).findFirst()
        .map(Authority::getTitle).orElse(null);
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public String getIcon() {
    return null;
  }

  @Override
  public Integer getOrder() {
    return null;
  }

  @Override
  public String getMenu() {
    return null;
  }

  @Override
  public List<String> getSubMenus() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAuthorities() {
    return authorities
        .stream()
        .map(Authority::getFullName)
        .collect(Collectors.toList());
  }
}
