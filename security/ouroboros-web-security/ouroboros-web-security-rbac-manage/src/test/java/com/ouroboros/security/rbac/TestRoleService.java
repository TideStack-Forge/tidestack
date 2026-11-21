package com.ouroboros.security.rbac;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.service.RoleService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:rbac_role_service;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
class TestRoleService {
  @Autowired
  RoleService service;

  @Autowired
  ApplicationContext applicationContext;

  @BeforeEach
  void refreshRoleModel() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(SystemSecurityIdentities.system()));
    applicationContext.publishEvent(new OuroborosModelRefreshingEvent(applicationContext));
    applicationContext.publishEvent(new OuroborosModelRefreshedEvent(applicationContext));
    assertTrue(TypedDataModelCenter.getDataModel(String.class, RoleEntity.class).isPresent());
  }

  @AfterEach
  void clearActor() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void add() {
    var model = role();
    var inserted = service.add(model);

    assertEquals(model.getId(), inserted.getId());
    assertEquals(model.getName(), service.getById(model.getId()).orElseThrow().getName());
  }

  @Test
  void getById() {
    var model = role();
    service.add(model);

    var persisted = service.getById(model.getId()).orElseThrow();
    assertEquals(model.getId(), persisted.getId());
    assertEquals(model.getName(), persisted.getName());
    assertTrue(service.getById("missing-role").isEmpty());
  }

  private RoleEntity role() {
    var model = new RoleEntity();
    model.setId("role-" + UUID.randomUUID());
    model.setName("role-name");
    return model;
  }
}
