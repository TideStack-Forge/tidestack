package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppBranchSwitchRequest;
import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.dev.app.service.impl.AppVersionServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

@ExtendWith(MockitoExtension.class)
class DirtyWorkspaceGuardTest {

  @Mock
  private VCSManagerCenter vcsManagerCenter;
  @Mock
  private VCSManager vcsManager;
  @Mock
  private AppVcsPolicyStore appVcsPolicyStore;
  @Mock
  private AppVcsFacade appVcsFacade;

  private AppVcsFacade realFacade;
  private AppVersionServiceImpl versionService;

  @BeforeEach
  void setUp() {
    realFacade = new AppVcsFacade(vcsManagerCenter, appVcsPolicyStore);
    versionService = new AppVersionServiceImpl(vcsManagerCenter, appVcsFacade);
  }

  @Test
  void switchBranchShouldBeBlockedWhenWorkspaceIsDirty() {
    stubDirtyWorkspace();
    Try<?> result = realFacade.switchBranch("crm", switchRequest("custom/premium"));

    assertTrue(result.isFailure());
    assertEquals("工作区存在未提交更改，禁止切换分支", result.getCause().getMessage());
    verify(vcsManager, never()).switchBranch(anyString());
  }

  @Test
  void switchBranchShouldRejectUnmanagedBranchEvenWhenWorkspaceIsClean() {
    when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    when(vcsManager.isWorkspaceClean()).thenReturn(Try.success(true));

    Try<?> result = realFacade.switchBranch("crm", switchRequest("feature/hotfix"));

    assertTrue(result.isFailure());
    assertEquals("仅允许切换到受管分支: master 或 custom/{editionCode}", result.getCause().getMessage());
    verify(vcsManager, never()).switchBranch(anyString());
  }

  @Test
  void pullShouldBeBlockedWhenWorkspaceIsDirty() {
    stubDirtyWorkspace();
    Try<?> result = realFacade.pull("crm");

    assertTrue(result.isFailure());
    assertEquals("工作区存在未提交更改，禁止Pull", result.getCause().getMessage());
    verify(vcsManager, never()).pull();
  }

  @Test
  void pushShouldBeBlockedWhenWorkspaceIsDirty() {
    stubDirtyWorkspace();
    Try<?> result = realFacade.push("crm");

    assertTrue(result.isFailure());
    assertEquals("工作区存在未提交更改，禁止Push", result.getCause().getMessage());
    verify(vcsManager, never()).push();
  }

  @Test
  void releaseShouldBeBlockedWhenWorkspaceIsDirty() {
    when(appVcsFacade.ensureWorkspaceClean("crm", "发版"))
        .thenReturn(Try.failure(new BusinessException("工作区存在未提交更改，禁止发版")));

    Try<Void> result = versionService.create("crm", new AppVersionDTO().setVersion("1.0.0"));

    assertTrue(result.isFailure());
    assertEquals("工作区存在未提交更改，禁止发版", result.getCause().getMessage());
    verify(vcsManager, never()).createTag(anyString(), anyString());
  }

  private static AppBranchSwitchRequest switchRequest(String branchName) {
    AppBranchSwitchRequest request = new AppBranchSwitchRequest();
    request.setBranchName(branchName);
    return request;
  }

  private void stubDirtyWorkspace() {
    when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    when(vcsManager.isWorkspaceClean()).thenReturn(Try.success(false));
  }
}
