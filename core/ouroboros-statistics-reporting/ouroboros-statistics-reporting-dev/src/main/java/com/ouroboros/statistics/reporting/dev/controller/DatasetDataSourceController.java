package com.ouroboros.statistics.reporting.dev.controller;

import com.ouroboros.statistics.reporting.dev.model.DatasetDataSourceRequest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.statistics.reporting.dev.service.DatasetDesignDataSourceService;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/dataset")
public class DatasetDataSourceController {
  private final DatasetDesignDataSourceService dataSources;

  public DatasetDataSourceController(DatasetDesignDataSourceService dataSources) {
    this.dataSources = dataSources;
  }

  @GetMapping("/datasources")
  public WebApiResult<List<SelectOption>> options(@PathVariable("appName") String appName) {
    return WebApiResult.ok(dataSources.options(appName));
  }

  @PostMapping("/datasource-tables")
  public WebApiResult<DatasetDesignDataSourceService.TableCatalog> tables(@PathVariable("appName") String appName,
                                                                         @RequestBody DatasetDataSourceRequest request) {
    return WebApiResult.ok(dataSources.tables(appName, request));
  }

  @GetMapping("/test-connections/platform")
  public WebApiResult<List<SelectOption>> platformOptions() {
    return WebApiResult.ok(dataSources.platformOptions());
  }

  @GetMapping("/test-connections/platform/{name}/environments")
  public WebApiResult<List<SelectOption>> environments(@PathVariable("name") String name) {
    return WebApiResult.ok(dataSources.environmentOptions(name));
  }

  @GetMapping("/test-connections/runtime")
  public WebApiResult<List<SelectOption>> runtimeOptions(@PathVariable("appName") String appName) {
    return WebApiResult.ok(dataSources.runtimeOptions(appName));
  }
}
