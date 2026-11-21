package com.ouroboros.dev.app.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.app.model.AuthorizeDebugProfileDTO;
import com.ouroboros.dev.app.model.AuthorizeDebugProfileRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadRequest;
import com.ouroboros.dev.app.model.DebugArtifactUploadResponseDTO;
import com.ouroboros.dev.app.service.runtime.DebugProfileAuthorizationService;
import com.ouroboros.dev.app.service.runtime.DebugSessionWorkspaceStorage;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}")
public class RuntimeDebugProfileController {
  private final DebugProfileAuthorizationService debugProfileAuthorizationService;
  private final DebugSessionWorkspaceStorage debugSessionWorkspaceStorage;

  public RuntimeDebugProfileController(DebugProfileAuthorizationService debugProfileAuthorizationService,
                                       DebugSessionWorkspaceStorage debugSessionWorkspaceStorage) {
    this.debugProfileAuthorizationService = debugProfileAuthorizationService;
    this.debugSessionWorkspaceStorage = debugSessionWorkspaceStorage;
  }

  @PostMapping("/authorize")
  public WebApiResult<AuthorizeDebugProfileDTO> authorize(@PathVariable String appName,
                                                          @RequestBody AuthorizeDebugProfileRequest request) {
    ensureDevKey(request);
    return WebApiResult.of(debugProfileAuthorizationService.authorize(appName, request.getDevKey()));
  }

  @PostMapping("/uploads")
  public WebApiResult<DebugArtifactUploadResponseDTO> upload(@PathVariable String appName,
                                                             @RequestBody DebugArtifactUploadRequest request) {
    if (request == null || StringUtils.isBlank(request.getDevKey())) {
      throw new BusinessException("devKey 不能为空");
    }
    if (request.getFiles() == null || request.getFiles().isEmpty()) {
      throw new BusinessException("files 不能为空");
    }
    return WebApiResult.of(debugProfileAuthorizationService.authorize(appName, request.getDevKey())
        .flatMap(authorization -> debugSessionWorkspaceStorage.store(authorization.getProfileId(), request)));
  }

  private void ensureDevKey(AuthorizeDebugProfileRequest request) {
    if (request == null || StringUtils.isBlank(request.getDevKey())) {
      throw new BusinessException("devKey 不能为空");
    }
  }
}
