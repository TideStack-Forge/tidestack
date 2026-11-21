package com.ouroboros.dev.app.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppBranchSwitchRequest;
import com.ouroboros.dev.app.model.AppManagedBranchCreateRequest;
import com.ouroboros.dev.app.model.AppVcsStateDTO;
import com.ouroboros.dev.app.model.AppVcsFileRequest;
import com.ouroboros.dev.app.service.vcs.AppVcsFacade;
import com.ouroboros.dev.app.service.vcs.AppVcsWorkspaceService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/vcs")
public class AppVcsOperationController {
  private final AppVcsFacade appVcsFacade;
  private final AppVcsWorkspaceService workspaceService;

  public AppVcsOperationController(AppVcsFacade appVcsFacade, AppVcsWorkspaceService workspaceService) {
    this.appVcsFacade = appVcsFacade;
    this.workspaceService = workspaceService;
  }

  @GetMapping("/state")
  public WebApiResult<AppVcsStateDTO> getState(@PathVariable("appName") String appName) {
    return WebApiResult.of(appVcsFacade.getState(appName));
  }

  @GetMapping("/branches")
  public ItemsWebApiResult<AppBranchDTO> getManagedBranches(@PathVariable("appName") String appName) {
    return ItemsWebApiResult.of(appVcsFacade.getManagedBranches(appName));
  }

  @PostMapping("/branch/create-managed")
  public WebApiResult<AppBranchDTO> createManagedBranch(@PathVariable("appName") String appName,
                                                        @RequestBody(required = false) AppManagedBranchCreateRequest request) {
    return WebApiResult.of(appVcsFacade.createManagedBranch(appName, request));
  }

  @PostMapping("/branch/switch")
  public WebApiResult<AppVcsStateDTO> switchBranch(@PathVariable("appName") String appName,
                                                   @RequestBody(required = false) AppBranchSwitchRequest request) {
    return WebApiResult.of(appVcsFacade.switchBranch(appName, request));
  }

  @PostMapping("/pull")
  public WebApiResult<AppVcsStateDTO> pull(@PathVariable("appName") String appName) {
    return WebApiResult.of(appVcsFacade.pull(appName));
  }

  @PostMapping("/push")
  public WebApiResult<AppVcsStateDTO> push(@PathVariable("appName") String appName) {
    return WebApiResult.of(appVcsFacade.push(appName));
  }

  @PostMapping("/workspace/commit")
  public WebApiResult<Boolean> commit(@PathVariable("appName") String appName,
                                      @RequestBody AppVcsFileRequest request) {
    return WebApiResult.of(workspaceService.commit(appName, request));
  }

  @GetMapping("/workspace/files")
  public ItemsWebApiResult<com.ouroboros.dev.app.model.AppVcsFileDTO> files(@PathVariable("appName") String appName,
      @RequestParam(defaultValue = "true") boolean onlyChanged) {
    return ItemsWebApiResult.of(workspaceService.getFiles(appName, onlyChanged));
  }

  @GetMapping("/workspace/file")
  public WebApiResult<java.util.Map<String, String>> file(@PathVariable("appName") String appName,
      @RequestParam String path) {
    return WebApiResult.of(workspaceService.getFileContent(appName, path));
  }

  @PostMapping("/workspace/unlock")
  public WebApiResult<Void> unlock(@PathVariable("appName") String appName,
                                   @RequestBody AppVcsFileRequest request) {
    return WebApiResult.of(workspaceService.unlock(appName, request));
  }
}
