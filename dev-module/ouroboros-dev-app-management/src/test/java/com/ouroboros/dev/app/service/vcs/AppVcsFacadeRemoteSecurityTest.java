package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsStateDTO;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

class AppVcsFacadeRemoteSecurityTest {

  private final VCSManagerCenter managerCenter = mock(VCSManagerCenter.class);
  private final AppVcsPolicyStore policyStore = mock(AppVcsPolicyStore.class);
  private final VCSManager vcsManager = mock(VCSManager.class);

  private AppVcsFacade facade;

  @BeforeEach
  void setUp() {
    facade = new AppVcsFacade(managerCenter, policyStore);
    when(managerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultBranch("master");
    when(policyStore.load("crm")).thenReturn(policy);
    when(vcsManager.getCurrentBranch()).thenReturn(Try.success("master"));
    when(vcsManager.isWorkspaceClean()).thenReturn(Try.success(true));
  }

  @Test
  void stateExposesTheCurrentCommitAndMessage() {
    var commit = mock(com.ouroboros.vcs.versioning.ChangeLog.class);
    when(commit.getId()).thenReturn("abc123");
    when(commit.getMessage()).thenReturn("更新模型");
    when(vcsManager.getChangeLogs(".", 0, 1)).thenReturn(java.util.List.of(commit));
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(new VCSConfig.RemoteConfig()));
    var state = facade.getState("crm").get();
    assertEquals("abc123", state.getCurrentRevision());
    assertEquals("更新模型", state.getLastCommitMessage());
  }

  @Test
  void currentBranchShouldActAsTrunkWhenPolicyDoesNotDeclareOne() {
    when(policyStore.load("crm")).thenReturn(new AppVcsPolicy());
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(new VCSConfig.RemoteConfig()));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertEquals("master", facade.getTrunkBranch("crm"));
    assertTrue(state.isBranchManaged());
    assertEquals("master", state.getReleaseTrack());
  }

  @Test
  void customBranchShouldBeManagedWhenPolicyDoesNotDeclareTrunk() {
    when(policyStore.load("crm")).thenReturn(new AppVcsPolicy());
    when(vcsManager.getCurrentBranch()).thenReturn(Try.success("custom/premium"));
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(new VCSConfig.RemoteConfig()));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertEquals("custom", state.getReleaseTrack());
    assertTrue(state.isBranchManaged());
    assertEquals("premium", state.getEditionCode());
  }

  @Test
  void stateShouldRedactCredentialBearingRemoteUris() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://deploy:secret@example.com/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertEquals("https://example.com/team/crm.git", state.getRemoteUri());
  }

  @Test
  void stateShouldHideUnsafeRuntimeRemoteTargets() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("file:///tmp/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertNull(state.getRemoteUri());
  }

  @Test
  void stateShouldHideQueryBearingRemoteUris() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://example.com/team/crm.git?token=secret");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertNull(state.getRemoteUri());
  }

  @Test
  void stateShouldHideInsecureTransportRemoteUris() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("http://example.com/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertNull(state.getRemoteUri());
  }

  @Test
  void stateShouldHideLoopbackRemoteUris() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("ssh://git@localhost/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    AppVcsStateDTO state = facade.getState("crm").get();

    assertNull(state.getRemoteUri());
  }

  @Test
  void pullShouldRejectUnsafeRuntimeRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("file:///tmp/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectInsecureTransportRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("http://example.com/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectLoopbackRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://127.0.0.1/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectShortLoopbackRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://127.1/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectNumericLoopbackRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://2130706433/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectExpandedIpv6LoopbackRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://[0:0:0:0:0:0:0:1]/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectIpv4MappedLoopbackRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://[::ffff:127.0.0.1]/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectHostnameStyleLoopbackRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("https://localhost.localdomain/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }

  @Test
  void pullShouldRejectInternalHostnameRemoteTargetsBeforeGitSync() {
    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri("ssh://git@git.internal/team/crm.git");
    when(vcsManager.getRemoteConfig()).thenReturn(Try.success(remoteConfig));

    Try<AppVcsStateDTO> result = facade.pull("crm");

    assertTrue(result.isFailure());
    verify(vcsManager, never()).setRemoteConfig(remoteConfig);
    verify(vcsManager, never()).pull();
  }
}
