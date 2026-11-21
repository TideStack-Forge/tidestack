package com.ouroboros.dev.app.model;

import java.util.List;

public class AppEditAccessConfigRequest {

  private List<AppEditAccessSubject> users;
  private List<AppEditAccessSubject> roles;
  private List<AppEditAccessSubject> organizations;

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
