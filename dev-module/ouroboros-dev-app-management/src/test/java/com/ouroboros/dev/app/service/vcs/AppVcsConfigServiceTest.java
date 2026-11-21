package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteDefinition;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManagerCenter;

class AppVcsConfigServiceTest {

  private final AppVcsPolicyStore policyStore = mock(AppVcsPolicyStore.class);
  private final AppVcsLocalBindingStore bindingStore = mock(AppVcsLocalBindingStore.class);
  private final VCSManagerCenter managerCenter = mock(VCSManagerCenter.class);

  private AppVcsConfigService service;

  @BeforeEach
  void setUp() {
    service = new AppVcsConfigService(policyStore, bindingStore, managerCenter);
  }

  @Test
  void getLocalBindingShouldNotFallbackLegacyFieldsToDifferentRemoteBinding() {
    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote("upstream");
    policy.setRemotes(Arrays.asList(
        remote("origin", "https://example.com/origin.git"),
        remote("upstream", "ssh://git@example.com/upstream.git")
    ));
    when(policyStore.load("crm")).thenReturn(policy);

    AppVcsLocalBinding binding = new AppVcsLocalBinding();
    binding.setBindings(Arrays.asList(httpBinding("origin", "origin-user", "origin-secret")));
    when(bindingStore.load("crm")).thenReturn(binding);

    AppVcsLocalBinding sanitized = service.getLocalBinding("crm");

    assertEquals("upstream", sanitized.getRemoteName());
    assertEquals(VCSConfig.AuthType.NONE, sanitized.getAuthType());
    assertNull(sanitized.getUsername());
    assertNull(sanitized.getPassword());
    assertEquals(1, sanitized.getBindings().size());
    assertEquals("origin", sanitized.getBindings().get(0).getRemoteName());
  }

  @Test
  void saveLocalBindingShouldRejectSshKeyForHttpsRemote() {
    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote("origin");
    policy.setRemotes(Arrays.asList(remote("origin", "https://example.com/origin.git")));
    when(policyStore.load("crm")).thenReturn(policy);

    AppVcsLocalBinding binding = new AppVcsLocalBinding();
    binding.setBindings(Arrays.asList(sshBinding("origin", "/tmp/id_rsa")));

    Try<Void> result = service.saveLocalBinding("crm", binding);

    assertTrue(result.isFailure());
    verify(bindingStore, never()).save("crm", binding);
    verify(managerCenter, never()).refresh("crm");
  }

  @Test
  void savePolicyShouldRejectHttpsRemoteWhenExistingBindingUsesSshKey() {
    AppVcsLocalBinding existingBinding = new AppVcsLocalBinding();
    existingBinding.setBindings(Arrays.asList(sshBinding("origin", "/tmp/id_rsa")));
    when(bindingStore.load("crm")).thenReturn(existingBinding);

    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote("origin");
    policy.setRemotes(Arrays.asList(remote("origin", "https://example.com/origin.git")));

    Try<Void> result = service.savePolicy("crm", policy);

    assertTrue(result.isFailure());
    verify(policyStore, never()).save("crm", policy);
    verify(managerCenter, never()).refresh("crm");
  }

  private static AppVcsRemoteDefinition remote(String name, String url) {
    AppVcsRemoteDefinition remote = new AppVcsRemoteDefinition();
    remote.setName(name);
    remote.setUrl(url);
    return remote;
  }

  private static AppVcsRemoteBinding httpBinding(String remoteName, String username, String password) {
    AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
    binding.setRemoteName(remoteName);
    binding.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
    binding.setUsername(username);
    binding.setPassword(password);
    return binding;
  }

  private static AppVcsRemoteBinding sshBinding(String remoteName, String sshKeyPath) {
    AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
    binding.setRemoteName(remoteName);
    binding.setAuthType(VCSConfig.AuthType.SSH_KEY);
    binding.setSshKeyPath(sshKeyPath);
    return binding;
  }
}
