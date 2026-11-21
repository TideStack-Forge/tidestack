package com.ouroboros.dev.environment.controller;

import jakarta.annotation.security.PermitAll;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.environment.Environment;
import com.ouroboros.dev.environment.EnvironmentAggregator;
import com.ouroboros.web.ItemsWebApiResult;

/**
 * @author lianshuze
 **/
@RestController
@RequestMapping("/api/dev-environment")
public class EnvironmentController {
  EnvironmentAggregator aggregator;

  public EnvironmentController(EnvironmentAggregator aggregator) {
    this.aggregator = aggregator;
  }

  @GetMapping
  @PermitAll
  public ItemsWebApiResult<Environment> search() {
    return ItemsWebApiResult.ok(aggregator.getAll());
  }
}
