package com.ouroboros.app.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.WebAppInfoModifier;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.util.SPI;
import com.ouroboros.web.WebApiResult;


/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/sys/web-app-info")
@SuppressWarnings({"unused"})
public class SystemInfoController {

  @Autowired
  ConfigurationService configurationService;

  @Anonymous
  @Operation(
      summary = "获取 Web 应用运行信息",
      description = "汇总当前 Web 应用的主题、名称以及各运行时提供的扩展信息。",
      tags = {"app-runtime"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回 Web 应用运行信息")
  })
  @GetMapping
  public WebApiResult<Map<String, Object>> webAppInfo() {
    Map<String, Object> result = new HashMap<>();
    result.put("currentTheme", "dark");
    result.put("appName", "Ouroboros");
    SPI.getCachedReversedServiceStream(WebAppInfoContributor.class)
        .map(WebAppInfoContributor::getWebAppInfo)
        .forEach(result::putAll);

    SPI.getCachedSortedServiceStream(WebAppInfoModifier.class)
        .forEach(modifier -> modifier.modify(result));

    return WebApiResult.ok(result);
  }
}
