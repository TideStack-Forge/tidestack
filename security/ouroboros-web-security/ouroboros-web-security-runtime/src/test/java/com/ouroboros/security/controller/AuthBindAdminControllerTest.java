package com.ouroboros.security.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;

@ExtendWith(MockitoExtension.class)
class AuthBindAdminControllerTest {

  @Mock
  private UserSsoService service;

  private AuthBindAdminController controller;

  @BeforeEach
  void setUp() {
    controller = new AuthBindAdminController();
    ReflectionTestUtils.setField(controller, "userSsoService", service);
  }

  @Test
  void listsOnlyTheRequestedUsersSafeBindingDetails() {
    UserSsoEntity binding = binding("binding-1");
    binding.setExternalAttributes(Map.of("private-attribute", "must-not-be-exposed"));
    when(service.findByUserIdentity("alice")).thenReturn(List.of(binding));

    JsonNode response = new ObjectMapper().valueToTree(controller.listBindings("alice"));

    assertThat(response.path("code").asInt()).isZero();
    JsonNode details = response.path("data").get(0);
    assertThat(details.path("id").asText()).isEqualTo("binding-1");
    assertThat(details.path("providerKey").asText()).isEqualTo("feishu-main");
    assertThat(details.path("externalId").asText()).isEqualTo("external-alice");
    assertThat(details.path("displayName").asText()).isEqualTo("Alice");
    assertThat(details.path("createdAt").asText()).isEqualTo("2026-11-16T10:00");
    assertThat(details.has("externalAttributes")).isFalse();
    assertThat(details.toString()).doesNotContain("must-not-be-exposed");
  }

  @Test
  void listsAnEmptyCollectionWhenTheUserHasNoBindings() {
    when(service.findByUserIdentity("alice")).thenReturn(List.of());

    assertThat(controller.listBindings("alice").getData()).isEqualTo(List.of());
  }

  @Test
  void deletesTheSelectedBindingThroughTheOriginalService() {
    when(service.findByUserIdentity("alice")).thenReturn(List.of(binding("binding-1")));
    when(service.deleteBinding("binding-1")).thenReturn(true);

    assertThat(controller.deleteBinding("alice", "binding-1").getCode()).isZero();
    verify(service).deleteBinding("binding-1");
  }

  @Test
  void refusesABindingThatDoesNotBelongToTheTargetUser() {
    when(service.findByUserIdentity("alice")).thenReturn(List.of(binding("binding-1")));

    assertThat(controller.deleteBinding("alice", "someone-elses-binding").getCode()).isEqualTo(40401);
    verify(service, never()).deleteBinding("someone-elses-binding");
  }

  @Test
  void doesNotReportSuccessWhenTheBindingDisappearsBeforeDeletion() {
    when(service.findByUserIdentity("alice")).thenReturn(List.of(binding("binding-1")));
    when(service.deleteBinding("binding-1")).thenReturn(false);

    assertThat(controller.deleteBinding("alice", "binding-1").getCode()).isEqualTo(40401);
  }

  @Test
  void propagatesStorageFailuresInsteadOfReportingSuccessfulUnbinding() {
    when(service.findByUserIdentity("alice")).thenReturn(List.of(binding("binding-1")));
    InternalException failure = new InternalException("Binding storage unavailable");
    when(service.deleteBinding("binding-1")).thenThrow(failure);

    assertThatThrownBy(() -> controller.deleteBinding("alice", "binding-1")).isSameAs(failure);
  }

  private UserSsoEntity binding(String id) {
    UserSsoEntity binding = new UserSsoEntity();
    binding.setId(id);
    binding.setUserIdentity("alice");
    binding.setSsoChannel("feishu-main");
    binding.setSsoUserIdentity("external-alice");
    binding.setExternalDisplayName("Alice");
    binding.setCreatedAt(LocalDateTime.of(2026, 11, 16, 10, 0));
    return binding;
  }
}
