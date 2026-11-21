package com.ouroboros.widget.controller;

import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.widget.WidgetDefinition;
import com.ouroboros.widget.service.WidgetDefinitionService;

@RestController
@RequestMapping("/api/widget-definition")
public class WidgetDefinitionController {
  private final WidgetDefinitionService widgetDefinitionService;

  public WidgetDefinitionController(WidgetDefinitionService widgetDefinitionService) {
    this.widgetDefinitionService = widgetDefinitionService;
  }

  @Operation(
      summary = "列出组件定义",
      description = "按场景筛选并返回运行时可用的组件定义列表。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组件定义列表")
  })
  @GetMapping
  public ItemsWebApiResult<WidgetDefinition> getAllWidgetDefinitions(
      @Parameter(description = "组件使用场景")
      @RequestParam(defaultValue = "") String scene) {
    var widgetDefinitions = widgetDefinitionService.getAllWidgetDefinitions();
    if (scene.isEmpty()) {
      return ItemsWebApiResult.ok(widgetDefinitions);
    }
    return ItemsWebApiResult.ok(widgetDefinitions.stream()
        .filter(widgetDefinition -> scene.equalsIgnoreCase(widgetDefinition.getScene()))
        .collect(Collectors.toList()));
  }
}
