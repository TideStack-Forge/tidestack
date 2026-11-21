package com.ouroboros.security.rbac.model;

import java.util.Collections;
import java.util.List;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

public class AnnotationAuthorityModel implements Authority {

  private final String fullName;
  private final List<String> apis;

  public AnnotationAuthorityModel(String fullName, List<String> apis) {
    this.fullName = fullName;
    this.apis = apis;
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  @Override
  public String getTitle() {
    return null;
  }

  @Override
  public AccessControlDefine getAccessControlDefine() {
    return AccessControlDefine.empty();
  }

  @Override
  public List<String> getApis() {
    return apis;
  }

  @Override
  public List<String> getUiViews() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getMenuItems() {
    return Collections.emptyList();
  }
}
