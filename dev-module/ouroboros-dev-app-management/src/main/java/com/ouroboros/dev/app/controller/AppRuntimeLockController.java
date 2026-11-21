package com.ouroboros.dev.app.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.app.model.AppArtifactBindingDTO;
import com.ouroboros.dev.app.model.ProfileArtifactOverlayDTO;
import com.ouroboros.dev.app.model.RepositoryRefDTO;
import com.ouroboros.dev.app.service.runtime.AppRuntimeLockFacade;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/runtime-lock")
public class AppRuntimeLockController {
  private final AppRuntimeLockFacade appRuntimeLockFacade;

  public AppRuntimeLockController(AppRuntimeLockFacade appRuntimeLockFacade) {
    this.appRuntimeLockFacade = appRuntimeLockFacade;
  }

  @PutMapping("/repository-refs")
  public WebApiResult<Void> replaceRepositoryRefs(@PathVariable("appName") String appName,
                                                  @RequestBody(required = false) List<RepositoryRefDTO> repositoryRefs) {
    return WebApiResult.of(appRuntimeLockFacade.replaceRepositoryRefs(appName, repositoryRefs));
  }

  @PutMapping("/artifact-bindings")
  public WebApiResult<Void> replaceAppArtifactBindings(@PathVariable("appName") String appName,
                                                       @RequestBody(required = false) List<AppArtifactBindingDTO> bindings) {
    return WebApiResult.of(appRuntimeLockFacade.replaceAppArtifactBindings(appName, bindings));
  }

  @PutMapping("/profiles/{profileId}/artifact-overlays")
  public WebApiResult<Void> replaceProfileArtifactOverlays(@PathVariable("appName") String appName,
                                                           @PathVariable("profileId") String profileId,
                                                           @RequestBody(required = false) List<ProfileArtifactOverlayDTO> overlays) {
    return WebApiResult.of(appRuntimeLockFacade.replaceProfileArtifactOverlays(appName, profileId, overlays));
  }

  @PostMapping("/profiles/{profileId}/runtime-kinds/{runtimeKind}/re-resolve")
  public WebApiResult<Void> requestResolve(@PathVariable("appName") String appName,
                                           @PathVariable("profileId") String profileId,
                                           @PathVariable("runtimeKind") String runtimeKind) {
    return WebApiResult.of(appRuntimeLockFacade.requestResolve(appName, profileId, runtimeKind));
  }
}
