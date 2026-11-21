package com.ouroboros.businessmodel.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ouroboros.model.annotation.LowCodeModel;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;

@LowCodeModel
public class BusinessModelAuthority implements Authority {
  boolean sealed = false;
  private boolean delegable;
  String fullName;
  String title;
  Integer order;
  BusinessModelAuthorityAccessControlDefine accessControlDefine = new BusinessModelAuthorityAccessControlDefine();
  List<String> apis = new ArrayList<>();
  List<String> uiViews = new ArrayList<>();
  List<String> menuItems = new ArrayList<>();

  public BusinessModelAuthority(String fullName, String title) {
    this.fullName = fullName;
    this.title = title;
  }

  @Override
  public boolean isDelegable() {
    return delegable;
  }

  public void setDelegable(boolean delegable) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot change delegable");
    }
    this.delegable = delegable;
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  @Override
  public String getTitle() {
    return title;
  }


  @Override
  public AccessControlDefine getAccessControlDefine() {
    return accessControlDefine;
  }

  @Override
  public List<String> getApis() {
    return Collections.unmodifiableList(apis);
  }

  @Override
  public List<String> getUiViews() {
    return Collections.unmodifiableList(uiViews);
  }

  @Override
  public List<String> getMenuItems() {
    return Collections.unmodifiableList(menuItems);
  }

  public void addACFilterDefine(AccessControlFilterDefine define) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot add filter define");
    }
    accessControlDefine.addFilterDefine(define);
  }

  public void setACCanControlFields(Boolean canControlFields) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot change canControlFields");
    }
    accessControlDefine.setCanControlFields(canControlFields);
  }

  public void addACCommonFields(List<AccessControlDefine.Field> commonFields) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot add common fields");
    }
    accessControlDefine.addCommonFields(commonFields);
  }

  public void addACControlledFields(List<AccessControlDefine.Field> controlledFields) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot add controlled fields");
    }
    accessControlDefine.addControlledFields(controlledFields);
  }

  public void addApi(String api) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot add api");
    }
    apis.add(api);
  }

  public void addUiView(String uiView) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot add ui view");
    }
    uiViews.add(uiView);
  }

  public void addMenuItem(String menuItem) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot add menu item");
    }
    menuItems.add(menuItem);
  }

  @Override
  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    if (sealed) {
      throw new IllegalStateException("Authority is sealed, cannot change order");
    }
    this.order = order;
  }

  public void seal() {
    sealed = true;
  }

  public static class BusinessModelAuthorityAccessControlDefine implements AccessControlDefine {
    private final List<AccessControlFilterDefine> filterDefine = new ArrayList<>();
    private Boolean canControlFields = false;
    private List<Field> commonFields = new ArrayList<>();
    private List<Field> controlledFields = new ArrayList<>();

    void addFilterDefine(AccessControlFilterDefine define) {
      filterDefine.add(define);
    }

    @Override
    public List<AccessControlFilterDefine> getFilterDefine() {
      return filterDefine;
    }


    @Override
    public Boolean getCanControlFields() {
      return canControlFields;
    }


    @Override
    public List<Field> getCommonFields() {
      return commonFields;
    }


    @Override
    public List<Field> getControlledFields() {
      return controlledFields;
    }

    void setCanControlFields(Boolean canControlFields) {
      this.canControlFields = canControlFields;
    }

    void addCommonFields(List<Field> commonFields) {
      this.commonFields.addAll(commonFields);
    }

    void addControlledFields(List<Field> controlledFields) {
      this.controlledFields.addAll(controlledFields);
    }
  }
}
