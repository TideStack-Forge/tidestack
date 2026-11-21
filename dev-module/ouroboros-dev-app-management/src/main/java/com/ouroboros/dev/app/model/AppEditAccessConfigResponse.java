package com.ouroboros.dev.app.model;

import java.util.List;

public class AppEditAccessConfigResponse {

  private boolean restricted;
  private boolean currentUserCanView;
  private boolean currentUserCanEdit;
  private List<AppEditAccessSubject> users;
  private List<AppEditAccessSubject> roles;
  private List<AppEditAccessSubject> organizations;

  public boolean isRestricted() {
    return restricted;
  }

  public void setRestricted(boolean restricted) {
    this.restricted = restricted;
  }

  public boolean isCurrentUserCanView() {
    return currentUserCanView;
  }

  public void setCurrentUserCanView(boolean currentUserCanView) {
    this.currentUserCanView = currentUserCanView;
  }

  public boolean isCurrentUserCanEdit() {
    return currentUserCanEdit;
  }

  public void setCurrentUserCanEdit(boolean currentUserCanEdit) {
    this.currentUserCanEdit = currentUserCanEdit;
  }

  public List<AppEditAccessSubject> getUsers() {
    return users;
  }

  public void setUsers(List<AppEditAccessSubject> users) {
    this.users = users;
  }

  public List<AppEditAccessSubject> getRoles() {
    return roles;
  }

  public void setRoles(List<AppEditAccessSubject> roles) {
    this.roles = roles;
  }

  public List<AppEditAccessSubject> getOrganizations() {
    return organizations;
  }

  public void setOrganizations(List<AppEditAccessSubject> organizations) {
    this.organizations = organizations;
  }
}
