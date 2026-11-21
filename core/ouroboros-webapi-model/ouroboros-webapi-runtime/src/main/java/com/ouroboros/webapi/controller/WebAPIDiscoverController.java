package com.ouroboros.webapi.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.DependsOn;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.util.SPI;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIModelProvider;

@RestController
@RequestMapping("/api/sys/webapi/discover")
@DependsOn("requestMappingHandlerMapping")
public class WebAPIDiscoverController {

  @Operation(
      summary = "发现已注册的 WebAPI 模型",
      description = "聚合当前运行时中所有已注册的 WebAPIModelProvider，并返回可导出的 WebAPI 模型列表。",
      tags = {"webapi-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回已注册的 WebAPI 模型列表")
  })
  @GetMapping("")
  public WebApiResult<List<WebAPIModel>> getWebapiModels() {
    var apis = SPI.getSortedServiceStream(WebAPIModelProvider.class)
        .flatMap(p -> p.getWebAPIModelMap().values().stream())
        .collect(Collectors.toList());
    return WebApiResult.ok(apis);
  }
}
