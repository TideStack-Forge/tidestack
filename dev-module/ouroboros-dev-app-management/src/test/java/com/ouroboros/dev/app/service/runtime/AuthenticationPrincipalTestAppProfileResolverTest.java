package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;

class AuthenticationPrincipalTestAppProfileResolverTest {

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldResolvePrincipalFromSeamAuthentication() {
    AuthenticationPrincipalTestAppProfileResolver resolver = new AuthenticationPrincipalTestAppProfileResolver();
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(OuroborosAuthenticationHolder.adapt(
            new UsernamePasswordAuthenticationToken("alice", "n/a", Collections.emptyList())));
      }
    });

    assertEquals("alice", resolver.resolveForCreate(new AppContainerRequest()));
  }

  @Test
  void shouldResolvePrincipalFromSpringSecurityContextWhenLegacyPrincipalExists() {
    AuthenticationPrincipalTestAppProfileResolver resolver = new AuthenticationPrincipalTestAppProfileResolver();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("legacy-principal", "n/a", Collections.emptyList())
    );

    assertEquals("legacy-principal", resolver.resolveForCreate(
        new AppContainerRequest()));
  }

  @Test
  void shouldNormalizeProfileNameFromPrincipal() {
    AuthenticationPrincipalTestAppProfileResolver resolver = new AuthenticationPrincipalTestAppProfileResolver();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("alice.dev@example.com", "n/a", Collections.emptyList())
    );

    assertEquals("alice_dev_example_com", resolver.resolveForCreate(new AppContainerRequest()));
    assertEquals("alice.dev@example.com", resolver.resolveCurrentUserIdentity());
    assertEquals("alice_dev_example_com", resolver.resolveProfileId("alice.dev@example.com"));
  }

  @Test
  void shouldFallbackToAnonymousWhenNoAuthenticationExists() {
    AuthenticationPrincipalTestAppProfileResolver resolver = new AuthenticationPrincipalTestAppProfileResolver();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();

    assertEquals(AnonymousAuthentication.getInstance().getPrincipal(), resolver.resolveForCreate(
        new AppContainerRequest()));
  }
}
