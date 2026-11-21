package com.ouroboros.documentmanagement.model.acl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionSetTest {

  @Test
  void removeShouldSupportRemovingTheLastPermission() {
    PermissionSet result = new PermissionSet(Permission.VIEW).remove(Permission.VIEW);

    assertTrue(result.isEmpty());
  }
}
