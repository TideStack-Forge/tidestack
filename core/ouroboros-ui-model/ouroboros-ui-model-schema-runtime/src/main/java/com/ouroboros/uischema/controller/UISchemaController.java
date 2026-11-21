package com.ouroboros.uischema.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.util.SPI;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping({"/api/amis-schema", "/api/ui-schema"})
public class UISchemaController {

  @Anonymous
  @Operation(
      summary = "按路径获取界面 Schema",
      description = "根据 UI Schema 资源路径查询运行时可用的界面 Schema 定义。",
      tags = {"ui-schema"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回界面 Schema"),
      @ApiResponse(responseCode = "404", description = "未找到对应界面 Schema")
  })
  @GetMapping("/{*path}")
  public WebApiResult<Object> getUISchema(
      @Parameter(description = "界面 Schema 资源路径", required = true)
      @PathVariable("path") String path,
      @RequestParam Map<String, String> requestParams) {
    return resolveUISchema(path, requestParams == null ? Collections.emptyMap() : requestParams);
  }

  public WebApiResult<Object> getUISchema(String path) {
    return resolveUISchema(path, Collections.emptyMap());
  }

  private WebApiResult<Object> resolveUISchema(String path, Map<String, String> requestParams) {
    var finalPath = path.startsWith("/") ? path : "/" + path;
    Map<String, Object> context = new LinkedHashMap<>(requestParams);
    context.remove("path");
    var foundSchema = SPI.getCachedReversedServiceStream(UISchemaProvider.class)
        .map(f -> f.getUISchema(finalPath, context))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
    return WebApiResult.of(foundSchema);
  }

  @Anonymous
  @Operation(
      summary = "按查询参数获取界面 Schema",
      description = "通过 path 查询参数查询运行时可用的界面 Schema 定义。",
      tags = {"ui-schema"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回界面 Schema"),
      @ApiResponse(responseCode = "404", description = "未找到对应界面 Schema")
  })
  @GetMapping("")
  public WebApiResult<Object> getUISchema2(
      @Parameter(description = "界面 Schema 资源路径", required = true)
      @RequestParam("path") String path,
      @RequestParam Map<String, String> requestParams) {
    return resolveUISchema(path, requestParams == null ? Collections.emptyMap() : requestParams);
  }

  public WebApiResult<Object> getUISchema2(String path) {
    return resolveUISchema(path, Collections.emptyMap());
  }
}
