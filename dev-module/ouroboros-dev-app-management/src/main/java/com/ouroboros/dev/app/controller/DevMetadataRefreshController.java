package com.ouroboros.dev.app.controller;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/metadata")
public class DevMetadataRefreshController {
  private final ApplicationEventPublisher eventPublisher;

  public DevMetadataRefreshController(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @PostMapping("/refresh")
  public WebApiResult<Boolean> refresh(@PathVariable("appName") String appName) {
    Objects.requireNonNull(appName, "appName");
    eventPublisher.publishEvent(new OuroborosContextRefreshEvent(this));
    return WebApiResult.ok(Boolean.TRUE);
  }
}
