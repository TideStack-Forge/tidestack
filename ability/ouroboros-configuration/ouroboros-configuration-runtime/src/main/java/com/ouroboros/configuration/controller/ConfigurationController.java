package com.ouroboros.configuration.controller;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.security.context.CurrentSecurityContextHolder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationModel;
import com.ouroboros.configuration.model.ConfigurationValueModel;
import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.configuration.util.ConfigurationEditorSchemaBuilder;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/sys/configuration")
public class ConfigurationController {

  private static final ConfigurationEditorSchemaBuilder SCHEMA_BUILDER = new ConfigurationEditorSchemaBuilder();

  @Autowired
  RuntimeConfigurationService service;

  @ModelAttribute
  public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @Operation(
      summary = "批量更新运行时配置",
      description = "按配置 key 批量更新运行时配置项，并返回更新后的配置模型。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "配置更新成功"),
      @ApiResponse(responseCode = "400", description = "配置参数不合法")
  })
  @PutMapping
  public WebApiResult<Map<String, ConfigurationModel>> update(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待更新的配置项列表", required = true)
      @RequestBody List<ConfigurationDTO> list) {
    if (list == null || list.isEmpty()) { throw new PlatformException("配置键不能为空！"); }
    Map<String, ConfigurationMutation> mutations = new LinkedHashMap<>();
    Map<String, String> expectedRevisions = new LinkedHashMap<>();
    for (ConfigurationDTO dto : list) {
      if (dto == null || StringUtils.isBlank(dto.getKey()) || dto.getOperation() == null) {
        throw new PlatformException("配置变更格式不合法");
      }
      if (StringUtils.isBlank(dto.getExpectedRevision())) {
        throw new PlatformException("[CONFIGURATION_REVISION_REQUIRED] 配置缺少 expectedRevision: " + dto.getKey());
      }
      boolean restore = dto.getOperation() == ConfigurationDTO.Operation.RESTORE_DEFAULT;
      if ((restore && dto.getValue() != null) || (!restore && dto.getValue() == null)) {
        throw new PlatformException("SET 必须提供值；恢复默认不能同时提供值");
      }
      ConfigurationMutation mutation = restore ? ConfigurationMutation.RestoreDefaults.INSTANCE
          : new ConfigurationMutation.Replace(dto.getValue());
      if (mutations.putIfAbsent(dto.getKey(), mutation) != null) {
        throw new PlatformException("配置键不能重复: " + dto.getKey());
      }
      expectedRevisions.put(dto.getKey(), dto.getExpectedRevision());
    }
    return WebApiResult.ok(service.updateManaged(mutations, getOperator(), expectedRevisions));
  }

  @Operation(
      summary = "查询全部运行时配置",
      description = "按 key 排序返回当前运行时的全部配置模型。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回配置列表")
  })
  @GetMapping
  public ItemsWebApiResult<ConfigurationModelVO> list() {
    var sortedList = service.getManagedConfigurationModels().values()
        .stream()
        .sorted(Comparator.comparing(ConfigurationModel::getKey))
        .map(ConfigurationModelVO::new)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(sortedList);
  }

  @Operation(
      summary = "按 key 获取配置值",
      description = "根据配置 key 查询单个配置值。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回配置值"),
      @ApiResponse(responseCode = "404", description = "指定配置不存在")
  })
  @GetMapping("/{key}")
  @PermitAll
  public WebApiResult<Map<String, Object>> get(
      @Parameter(description = "配置项 key", required = true)
      @PathVariable("key") String key) {
    var result = service.getManagedConfigurationModel(key)
        .map(model -> Collections.singletonMap("value", model.getValue()));
    return WebApiResult.of(404, "指定配置不存在", result);
  }

  @Operation(
      summary = "获取配置值类型枚举",
      description = "返回配置系统支持的值类型枚举列表。",
      tags = {"configuration-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回值类型枚举")
  })
  @GetMapping("/value-type")
  public WebApiResult<List<ConfigValueType>> valueTypes() {
    var types = Arrays.asList(ConfigValueType.values());
    return WebApiResult.ok(types);
  }

  /**
   * 获取操作人
   *
   * @return
   */
  private String getOperator() {
    return CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity();
  }

  public static class ConfigurationModelVO {
    @JsonIgnore
    private final ConfigurationModel model;

    public ConfigurationModelVO(ConfigurationModel model) {
      this.model = model;
    }

    public String getKey() {
      return model.getKey();
    }

    public Object getValue() {
      return model.getValue();
    }

    public String getRevision() {
      return model.getRevision();
    }

    /**
     * 获取原始配置值（未经 SpEL 解析）
     * 用于配置管理界面显示和编辑
     *
     * @return 原始配置值
     */
    public String getRawValue() {
      if (model.getConfigValue() != null) {
        return model.getConfigValue().getValue();
      }
      return model.getMetadata().getDefaultValue();
    }

    public ConfigurationMetadata getMetadata() {
      return model.getMetadata();
    }

    public ConfigurationValueModel getConfigValue() {
      return model.getConfigValue();
    }

    public SchemaNode getEditorSchema() {
      return SCHEMA_BUILDER.build(getMetadata());
    }
  }
}
