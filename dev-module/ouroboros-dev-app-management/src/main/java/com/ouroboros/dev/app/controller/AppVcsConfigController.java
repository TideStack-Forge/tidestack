package com.ouroboros.dev.app.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.service.vcs.AppVcsConfigService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/vcs/config")
public class AppVcsConfigController {
  private final AppVcsConfigService appVcsConfigService;

  public AppVcsConfigController(AppVcsConfigService appVcsConfigService) {
    this.appVcsConfigService = appVcsConfigService;
  }

  @GetMapping("/policy")
  public WebApiResult<AppVcsPolicy> getPolicy(@PathVariable("appName") String appName) {
    return WebApiResult.ok(appVcsConfigService.getPolicy(appName));
  }

  @PutMapping("/policy")
  public WebApiResult<Void> savePolicy(@PathVariable("appName") String appName,
                                       @RequestBody(required = false) AppVcsPolicy policy) {
    return WebApiResult.of(appVcsConfigService.savePolicy(appName, policy));
  }

  @GetMapping("/local-binding")
  public WebApiResult<AppVcsLocalBinding> getLocalBinding(@PathVariable("appName") String appName) {
    return WebApiResult.ok(appVcsConfigService.getLocalBinding(appName));
  }

  @PutMapping("/local-binding")
  public WebApiResult<Void> saveLocalBinding(@PathVariable("appName") String appName,
                                             @RequestBody(required = false) AppVcsLocalBinding binding) {
    return WebApiResult.of(appVcsConfigService.saveLocalBinding(appName, binding));
  }
}
