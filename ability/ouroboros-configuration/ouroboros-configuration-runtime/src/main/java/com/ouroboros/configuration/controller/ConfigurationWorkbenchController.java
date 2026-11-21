package com.ouroboros.configuration.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchGroupPanel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchItemPanel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchTreeNode;
import com.ouroboros.configuration.service.ConfigurationWorkbenchService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/configuration/workbench")
public class ConfigurationWorkbenchController {

  @Autowired
  private ConfigurationWorkbenchService workbenchService;

  @org.springframework.web.bind.annotation.ModelAttribute
  public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @Operation(
      summary = "获取配置工作台树",
      description = "返回配置工作台左侧树结构。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回工作台树")
  })
  @GetMapping("/tree")
  public ItemsWebApiResult<ConfigurationWorkbenchTreeNode> tree() {
    return ItemsWebApiResult.ok(workbenchService.getTree());
  }

  @Operation(
      summary = "获取配置工作台分组面板",
      description = "根据路径查询配置工作台分组面板。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回分组面板")
  })
  @GetMapping("/group")
  public WebApiResult<ConfigurationWorkbenchGroupPanel> group(
      @Parameter(description = "工作台分组路径")
      @RequestParam(name = "path", required = false) String path) {
    return WebApiResult.of(workbenchService.getGroupPanel(path));
  }

  @Operation(
      summary = "获取配置工作台条目面板",
      description = "根据配置 key 查询单个配置项的工作台面板。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回配置项面板"),
      @ApiResponse(responseCode = "404", description = "配置项不存在")
  })
  @GetMapping("/item/{key}")
  public WebApiResult<ConfigurationWorkbenchItemPanel> item(
      @Parameter(description = "配置项 key", required = true)
      @PathVariable("key") String key) {
    return WebApiResult.of(404, "配置项不存在", workbenchService.getItemPanel(key));
  }
}
