package com.ouroboros.dev.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppVcsStateDTO;
import com.ouroboros.dev.app.service.vcs.AppVcsFacade;

class AppVcsOperationControllerTest {

  private final AppVcsFacade appVcsFacade = mock(AppVcsFacade.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AppVcsOperationController(appVcsFacade,
        mock(com.ouroboros.dev.app.service.vcs.AppVcsWorkspaceService.class))).build();
  }

  @Test
  void stateEndpointShouldExposeCurrentBranchAndWorkspaceStatus() throws Exception {
    when(appVcsFacade.getState("crm")).thenReturn(Try.success(new AppVcsStateDTO()
        .setCurrentBranch("custom/premium")
        .setReleaseTrack("custom")
        .setWorkspaceClean(true)
        .setRemoteBranch("custom/premium")));

    mockMvc.perform(get("/api/dev/crm/vcs/state"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.currentBranch").value("custom/premium"))
        .andExpect(jsonPath("$.data.releaseTrack").value("custom"))
        .andExpect(jsonPath("$.data.workspaceClean").value(true));
  }

  @Test
  void branchesEndpointShouldReturnManagedBranches() throws Exception {
    when(appVcsFacade.getManagedBranches("crm")).thenReturn(Try.success(Arrays.asList(
        new AppBranchDTO().setBranchName("master").setReleaseTrack("master").setManaged(true).setCurrent(true),
        new AppBranchDTO().setBranchName("custom/premium").setReleaseTrack("custom").setEditionCode("premium").setManaged(true).setCurrent(false)
    )));

    mockMvc.perform(get("/api/dev/crm/vcs/branches"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].branchName").value("master"))
        .andExpect(jsonPath("$.data.items[1].branchName").value("custom/premium"));
  }

  @Test
  void createManagedBranchEndpointShouldReturnCreatedBranch() throws Exception {
    when(appVcsFacade.createManagedBranch(any(), any())).thenReturn(Try.success(
        new AppBranchDTO().setBranchName("custom/premium").setReleaseTrack("custom").setEditionCode("premium").setManaged(true)
    ));

    mockMvc.perform(post("/api/dev/crm/vcs/branch/create-managed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"editionCode\":\"premium\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.branchName").value("custom/premium"))
        .andExpect(jsonPath("$.data.releaseTrack").value("custom"));
  }

  @Test
  void switchPullAndPushEndpointsShouldReturnUpdatedState() throws Exception {
    AppVcsStateDTO switched = new AppVcsStateDTO().setCurrentBranch("custom/premium").setReleaseTrack("custom").setWorkspaceClean(true);
    AppVcsStateDTO pulled = new AppVcsStateDTO().setCurrentBranch("custom/premium").setReleaseTrack("custom").setWorkspaceClean(true);
    AppVcsStateDTO pushed = new AppVcsStateDTO().setCurrentBranch("custom/premium").setReleaseTrack("custom").setWorkspaceClean(true);
    when(appVcsFacade.switchBranch(any(), any())).thenReturn(Try.success(switched));
    when(appVcsFacade.pull("crm")).thenReturn(Try.success(pulled));
    when(appVcsFacade.push("crm")).thenReturn(Try.success(pushed));

    mockMvc.perform(post("/api/dev/crm/vcs/branch/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"branchName\":\"custom/premium\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentBranch").value("custom/premium"));

    mockMvc.perform(post("/api/dev/crm/vcs/pull"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentBranch").value("custom/premium"));

    mockMvc.perform(post("/api/dev/crm/vcs/push"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentBranch").value("custom/premium"));
  }
}
