package com.ouroboros.security.rbac;

import java.util.List;

public interface UserRoleProvider {
  List<User> getUsersByRole(String roleId);

  List<Role> getRolesByUser(String userId);
}
