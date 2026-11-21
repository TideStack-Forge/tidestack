package com.ouroboros.resource.dev.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.resource.dev.service.ResourceDevService;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.TreeSelectOption;
import com.ouroboros.web.WebApiResult;

import io.vavr.control.Try;

@RestController
@RequestMapping("/api/dev/{appName}/resource/directories")
public class ResourceDirectoryController {

  private final ResourceDevService resourceDevService;

  public ResourceDirectoryController(ResourceDevService resourceDevService) {
    this.resourceDevService = resourceDevService;
  }

  @GetMapping
  public ItemsWebApiResult<TreeSelectOption> listDirectories(@PathVariable("appName") String appName,
                                                             @RequestParam(value = "scope", required = false)
                                                             String scope) {
    Try<java.util.List<TreeSelectOption>> result =
        Try.of(() -> resourceDevService.listDirectories(parseScope(scope)));
    return ItemsWebApiResult.of(result);
  }

  @PostMapping
  public WebApiResult<Void> createDirectory(@PathVariable("appName") String appName,
                                            @RequestParam("scope") String scope,
                                            @RequestParam("directory") String directory) {
    Try<Void> result = Try.of(() -> parseScope(scope))
        .flatMap(resourceScope -> resourceDevService.createDirectory(resourceScope, directory));
    return WebApiResult.of(result);
  }

  private ResourceScope parseScope(String scope) {
    return StringUtils.isBlank(scope) ? null : ResourceScope.fromValue(scope);
  }
}
