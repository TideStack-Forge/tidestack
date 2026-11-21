package com.ouroboros.dev.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;
import com.ouroboros.vcs.VCSManagerCenter;

class BuiltinPositionDevelopServiceTest {
  @TempDir
  Path workspace;

  @AfterEach
  void cleanup() {
    DevAppHolder.removeAppName();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void shouldPersistPositionDefinitionUnderOrganizationStructurePositionsDirectory() throws Exception {
    DevAppHolder.setAppName("demo-app");
    initGitRepository(workspace);
    BuiltinPositionDevelopService service = newService(workspace);

    PositionDefinition definition = new PositionDefinition();
    definition.setKey("engineer");
    definition.setName("工程师");
    definition.setLevel("P5");

    Try<PositionDefinition> result = service.create(definition, "create position");

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(workspace.resolve("organization-structure/positions/engineer.position.json")));
  }

  @Test
  void shouldRejectPositionDefinitionWithoutKey() throws Exception {
    DevAppHolder.setAppName("demo-app");
    initGitRepository(workspace);
    BuiltinPositionDevelopService service = newService(workspace);

    PositionDefinition definition = new PositionDefinition();
    definition.setName("工程师");

    Try<PositionDefinition> result = service.create(definition, "invalid position");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("岗位编码不能为空"));
  }

  private BuiltinPositionDevelopService newService(Path appHome) {
    CurrentSecurityContextHolder.setDelegate(securityContext(identity("tester")));

    VCSManagerCenter center = new VCSManagerCenter();
    AppHomeResolver resolver = appName -> appHome.toString();
    ReflectionTestUtils.setField(center, "appHomeResolver", resolver);

    BuiltinPositionDevelopService service = new BuiltinPositionDevelopService();
    ReflectionTestUtils.setField(service, "vcsManagerCenter", center);
    ReflectionTestUtils.setField(service, "publisher", (ApplicationEventPublisher) event -> {
    });
    return service;
  }

  private void initGitRepository(Path path) throws Exception {
    try (Git ignored = Git.init().setDirectory(path.toFile()).call()) {
      // repository initialized for VCS-backed develop service
    }
  }

  private static CurrentSecurityContext securityContext(SecurityIdentity identity) {
    return () -> Optional.ofNullable(identity);
  }

  private static SecurityIdentity identity(String userIdentity) {
    return new SecurityIdentity() {
      private final User user = new User() {
        @Override
        public String getUserIdentity() {
          return userIdentity;
        }
      };

      @Override
      public String getPrincipal() {
        return userIdentity;
      }

      @Override
      public String getUserIdentity() {
        return userIdentity;
      }

      @Override
      public User getUserDetail() {
        return user;
      }

      @Override
      public Claims getClaims() {
        return Claims.empty();
      }

      @Override
      public Subject getSubject() {
        return null;
      }

      @Override
      public List<Role> getRoles() {
        return Collections.emptyList();
      }
    };
  }
}
