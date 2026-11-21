package com.ouroboros.widget.manage.controller;

import java.util.Collections;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Dates;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.widget.WidgetSchema;
import com.ouroboros.widget.manage.model.WidgetSchemaModel;
import com.ouroboros.widget.manage.pojo.dto.WidgetSchemaManageDTO;
import com.ouroboros.widget.manage.service.WidgetSchemaService;
import com.ouroboros.widget.service.WidgetDefinitionService;

@RestController
@RequestMapping("/api/widget-schema")
public class WidgetSchemaManageController {
  private final WidgetSchemaService widgetSchemaService;
  private final WidgetDefinitionService widgetDefinitionService;
  public WidgetSchemaManageController(WidgetSchemaService widgetSchemaService, WidgetDefinitionService widgetDefinitionService) {
    this.widgetSchemaService = widgetSchemaService;
    this.widgetDefinitionService = widgetDefinitionService;
  }

  @Operation(
      summary = "查询组件 Schema 管理列表",
      description = "按关键字筛选组件定义，并合并现有组件 Schema 的管理信息。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组件 Schema 管理列表")
  })
  @GetMapping
  public ItemsWebApiResult<WidgetSchemaManageDTO> getWidgetSchemaManageList(
      @Parameter(description = "组件关键字")
      @RequestParam(value = "keyword", defaultValue = "") String keyword) {
    var widgetDefinitions = widgetDefinitionService.getAllWidgetDefinitions()
        .stream()
        .filter(def -> keyword.isEmpty()
            || StringUtils.containsIgnoreCase(def.getName(), keyword)
            || StringUtils.containsIgnoreCase(def.getTitle(), keyword)
            || StringUtils.containsIgnoreCase(def.getCategory(), keyword)
            || StringUtils.containsIgnoreCase(def.getScene(), keyword)
            || StringUtils.containsIgnoreCase(def.getDescription(), keyword)
        )
        .collect(Collectors.toList());
    if (widgetDefinitions.isEmpty()) {
      return ItemsWebApiResult.ok(Collections.emptyList());
    }
    var widgetSchemas = widgetSchemaService.getAllWidgetSchemas()
        .stream()
        .collect(() -> new TreeMap<String, WidgetSchemaModel>(String::compareToIgnoreCase), (m, schema) -> m.put(schema.getName(), schema), TreeMap::putAll);

    var remappedDefs = widgetDefinitions.stream()
        .map(def -> {
          var widgetSchema = widgetSchemas.get(def.getName());
          if (widgetSchema == null) {
            return new WidgetSchemaManageDTO()
                .setDefinition(def)
                .setFromWidgetSchema(false);
          }
          return new WidgetSchemaManageDTO()
              .setId(widgetSchema.getId())
              .setDefinition(def)
              .setFromWidgetSchema(true)
              .setCreatedAt(Optional.ofNullable(widgetSchema.getCreatedAt()).map(Dates::toString).orElse(""))
              .setUpdatedAt(Optional.ofNullable(widgetSchema.getUpdatedAt()).map(Dates::toString).orElse(""));
        })
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(remappedDefs);
  }

  @Operation(
      summary = "创建组件 Schema",
      description = "新增一个组件 Schema 定义。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "组件 Schema 创建成功"),
      @ApiResponse(responseCode = "400", description = "组件名称已存在或参数不合法")
  })
  @PostMapping
  public WebApiResult<Void> addWidgetSchema(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "组件 Schema 定义", required = true)
      @RequestBody WidgetSchema widgetSchema) {
    if (!widgetSchemaService.checkNameAvailable(widgetSchema.getName())) {
      return WebApiResult.fail("名称已存在");
    }
    var result = widgetSchemaService.addWidgetSchema(widgetSchema);
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "更新组件 Schema",
      description = "根据组件 Schema ID 更新定义。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "组件 Schema 更新成功"),
      @ApiResponse(responseCode = "404", description = "组件 Schema 不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<Void> updateWidgetSchema(
      @Parameter(description = "组件 Schema ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的组件 Schema 定义", required = true)
      @RequestBody WidgetSchema widgetSchema) {
    if (!widgetSchemaService.getWidgetSchema(id).isPresent()) {
      return notFound();
    }
    var result = widgetSchemaService.updateWidgetSchema(id, widgetSchema);
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "获取组件 Schema 详情",
      description = "根据组件 Schema ID 查询详细定义。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组件 Schema 详情"),
      @ApiResponse(responseCode = "404", description = "组件 Schema 不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<WidgetSchema> getWidgetSchema(
      @Parameter(description = "组件 Schema ID", required = true)
      @PathVariable("id") String id) {
    var result = widgetSchemaService.getWidgetSchema(id);
    return WebApiResult.of(404, "组件 Schema 不存在", result);
  }

  @Operation(
      summary = "列出组件生成器",
      description = "返回所有可用的组件 Schema 生成器选项。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组件生成器列表")
  })
  @GetMapping("/generators")
  public ItemsWebApiResult<SelectOption> getAllWidgetDefinition() {
    var options = widgetSchemaService.getWidgetDefinitionGenerators().stream()
        .map(generator -> new SelectOption(generator.getName(), generator.getLabel()))
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(options);
  }

  @Operation(
      summary = "获取生成器表单项",
      description = "根据组件生成器名称返回其参数表单项定义。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回生成器表单项"),
      @ApiResponse(responseCode = "404", description = "组件生成器不存在")
  })
  @GetMapping("/generators/{generatorName}/form-items")
  public ItemsWebApiResult<SchemaNode> getFormItems(
      @Parameter(description = "组件生成器名称", required = true)
      @PathVariable("generatorName") String generatorName) {
    boolean exists = widgetSchemaService.getWidgetDefinitionGenerators().stream()
        .anyMatch(generator -> generator.getName().equals(generatorName));
    if (!exists) {
      return ItemsWebApiResult.error(404, "组件生成器不存在");
    }
    var formItems = widgetSchemaService.getFormItems(generatorName);
    return ItemsWebApiResult.ok(formItems);
  }

  private <T> WebApiResult<T> notFound() {
    return WebApiResult.error(404, "组件 Schema 不存在");
  }
}
