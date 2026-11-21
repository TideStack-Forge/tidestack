package com.ouroboros.dynamicform.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.data.dsl.query.Query;
import com.ouroboros.dynamicform.controller.dto.DynamicFormRequest;
import com.ouroboros.dynamicform.model.DynamicForm;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.util.Dates;
import com.ouroboros.web.*;

@RestController
@RequestMapping("/api/dynamic-form")
public class DynamicFormDefineController {
  @Autowired
  DynamicFormDefineService dynamicFormDefineService;

  @Autowired
  FieldTypeCatalog fieldTypeCatalog;

  /**
   * 搜索动态表单定义
   *
   * @param params
   * @return
   */
  @Operation(summary = "分页查询动态表单定义", description = "根据筛选条件分页查询动态表单定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回动态表单定义分页结果")
  })
  @GetMapping("")
  public PagedWebApiResult<DynamicForm> search(
      @Parameter(name = "params", description = "分页、排序与筛选参数")
      @RequestParam Map<String, Object> params) {
    var pagination = RequestUtil.getSkipLimit(params);
    var order = RequestUtil.getOrder(params);
    var where = RequestUtil.getWhere(params);
    var builder = Query.from("DynamicForm")
        .where(where)
        .clause("OFFSET", pagination._1())
        .clause("LIMIT", pagination._2())
        .clause("OMIT", "fields");
    if (order.size() == 1) {
      var orderField = order.keySet().stream().findFirst().get();
      var orderDir = order.get(orderField);
      builder.clause("ORDER", Map.of(orderField, orderDir));
    }
    var query = builder.build();

    var total = dynamicFormDefineService.getTotal(where);
    var items = dynamicFormDefineService.search(query);
    return PagedWebApiResult.ok(total, items);
  }

  /**
   * 获取单个动态表单定义
   *
   * @param id
   * @return
   */
  @Operation(summary = "获取动态表单定义详情", description = "根据动态表单 ID 查询定义详情。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回动态表单定义"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<DynamicForm> get(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id) {
    var result = dynamicFormDefineService.getDynamicFormById(id);
    return WebApiResult.of(404, "动态表单定义不存在", result);
  }

  /**
   * 添加动态表单定义
   *
   * @param newForm
   * @return
   */
  @Operation(summary = "创建动态表单定义", description = "新增一个动态表单定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单定义创建成功"),
      @ApiResponse(responseCode = "400", description = "动态表单定义参数不合法")
  })
  @PostMapping("")
  public WebApiResult<DynamicForm> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "动态表单定义", required = true)
      @RequestBody DynamicFormRequest request) {
    var name = request.getName();
    if (name == null || name.trim().isEmpty()) {
      return badRequest("表单名称不能为空");
    }
    if (dynamicFormDefineService.getDynamicFormByName(name).isPresent()) {
      return badRequest("动态表单定义已存在");
    }
    
    var newForm = new DynamicForm();
    newForm.setName(request.getName());
    newForm.setTitle(request.getTitle());
    newForm.setFields(request.getFields());
    newForm.setOptions(request.getOptions());
    
    if (ObjectUtils.isEmpty(newForm.getFields())) {
      newForm.setStatus("未部署");
    }
    newForm.setCreatedAt(Dates.now());
    newForm.setCreatedBy(getUser());
    newForm.setUpdatedAt(Dates.now());
    newForm.setUpdatedBy(getUser());

    var result = dynamicFormDefineService.add(newForm);
    return WebApiResult.of(result);
  }

  /**
   * 更新动态表单定义
   *
   * @param id
   * @param form
   * @return
   */
  @Operation(summary = "更新动态表单定义", description = "根据动态表单 ID 更新定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单定义更新成功"),
      @ApiResponse(responseCode = "400", description = "动态表单定义参数不合法或状态不允许更新"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<DynamicForm> update(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的动态表单定义", required = true)
      @RequestBody DynamicFormRequest request) {
    var oldForm = dynamicFormDefineService.getDynamicFormById(id);
    if (!oldForm.isPresent()) {
      return notFound("动态表单定义不存在");
    }
    if (!("未部署".equals(oldForm.get().getStatus()) || "已下线".equals(oldForm.get().getStatus()))) {
      return badRequest("动态表单当前状态不允许修改");
    }
    if (ObjectUtils.isEmpty(request.getName())) {
      return badRequest("表单名称不能为空");
    }
    if (!request.getName().trim().equals(oldForm.get().getName())) {
      if (dynamicFormDefineService.getDynamicFormByName(request.getName().trim()).isPresent()) {
        return badRequest("动态表单定义已存在");
      }
    }

    var form = new DynamicForm();
    form.setName(request.getName());
    form.setTitle(request.getTitle());
    form.setFields(request.getFields());
    form.setOptions(request.getOptions());

    // 原表单已被部署的表单项不允许删除
    if (oldForm.get().getFields().stream()
        .filter(FormField::getDeployed)
        .anyMatch(deployedField -> form.getFields().stream()
            .noneMatch(newField -> Objects.equals(newField.getName(), deployedField.getName())))) {
      return badRequest("已部署的表单项不允许删除");
    }

    // 把原表单字段的已部署状态同步到到新表单同名字段上
    oldForm.get().getFields().forEach(oldField -> {
      form.getFields().stream()
          .filter(field -> Objects.equals(field.getName(), oldField.getName()))
          .findFirst()
          .ifPresent(field -> field.setDeployed(oldField.getDeployed()));
    });

    // 若原表单为已下线状态，则不允许再改名
    if ("已下线".equals(oldForm.get().getStatus())) {
      form.setName(oldForm.get().getName());
    }

    form.setId(id);
    form.setUpdatedAt(Dates.now());
    form.setUpdatedBy(getUser());
    form.setCreatedBy(null);
    form.setCreatedAt(null);
    var result = dynamicFormDefineService.update(form);
    if (!result) {
      return notFound("动态表单定义不存在");
    }
    return WebApiResult.of(404, "动态表单定义不存在", dynamicFormDefineService.getDynamicFormById(id));
  }

  /**
   * 部署表单
   *
   * @param id 表单ID
   * @return
   */
  @Operation(summary = "部署动态表单", description = "部署指定动态表单定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单部署成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不允许部署"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @PostMapping("/{id}/deploy")
  public WebApiResult<Boolean> deploy(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id) {
    var form = dynamicFormDefineService.getDynamicFormById(id);
    if (!form.isPresent()) {
      return notFound("表单不存在");
    }
    if (!"未部署".equals(form.get().getStatus())) {
      return badRequest("表单已部署");
    }
    var result = dynamicFormDefineService.deploy(id);
    return WebApiResult.ok(result);
  }

  @Operation(summary = "下线动态表单", description = "将已部署的动态表单下线。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单下线成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不允许下线"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @PostMapping("/{id}/take-down")
  public WebApiResult<Boolean> takeDown(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id) {
    var form = dynamicFormDefineService.getDynamicFormById(id);
    if (!form.isPresent()) {
      return notFound("表单不存在");
    }
    if (!"已部署".equals(form.get().getStatus())) {
      return badRequest("表单未部署");
    }
    var result = dynamicFormDefineService.takeDown(id);
    return WebApiResult.ok(result);
  }

  /**
   * 重新部署表单
   *
   * @param id
   * @return
   */
  @Operation(summary = "重新部署动态表单", description = "重新部署已下线的动态表单定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单重新部署成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不允许重新部署"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @PostMapping("/{id}/re-deploy")
  public WebApiResult<Boolean> reDeploy(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id) {
    var form = dynamicFormDefineService.getDynamicFormById(id);
    if (!form.isPresent()) {
      return notFound("表单不存在");
    }
    if (!"已下线".equals(form.get().getStatus())) {
      return badRequest("当前表单不可重新部署");
    }
    var result = dynamicFormDefineService.reDeploy(id);
    return WebApiResult.ok(result);
  }

  @Operation(summary = "归档动态表单", description = "归档指定动态表单定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单归档成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不允许归档"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @PostMapping("/{id}/archive")
  public WebApiResult<Boolean> archive(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id) {
    var form = dynamicFormDefineService.getDynamicFormById(id);
    if (!form.isPresent()) {
      return notFound("表单不存在");
    }
    if (!"已部署".equals(form.get().getStatus())) {
      return badRequest("当前表单不可归档");
    }
    var result = dynamicFormDefineService.archive(id);
    return WebApiResult.ok(result);
  }

  /**
   * 删除表单
   *
   * @param id
   * @return
   */
  @Operation(summary = "删除动态表单定义", description = "删除一个未部署的动态表单定义。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单定义删除成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不允许删除"),
      @ApiResponse(responseCode = "404", description = "动态表单定义不存在")
  })
  @DeleteMapping("/{id}")
  public WebApiResult<Boolean> delete(
      @Parameter(description = "动态表单 ID", required = true)
      @PathVariable("id") Integer id) {
    var form = dynamicFormDefineService.getDynamicFormById(id);
    if (!form.isPresent()) {
      return notFound("表单不存在");
    }
    if (!Objects.equals(form.get().getStatus(), "未部署")) {
      return badRequest("表单已部署，不能删除");
    }

    var result = dynamicFormDefineService.delete(id);
    return WebApiResult.ok(result);
  }

  /**
   * 获取所有字段类型
   *
   * @return
   */
  @Operation(summary = "列出字段类型", description = "返回动态表单可选字段类型列表。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回字段类型列表")
  })
  @GetMapping("/field-types")
  public ItemsWebApiResult<SelectOption> getFields() {
    var options = fieldTypeCatalog.listMetadata(FieldTypeCatalogContext.runtime()).stream()
        .filter(field -> !field.getIsPrimaryKeyType())
        .sorted(Comparator.comparing(a -> Optional.ofNullable(a.getOrder()).orElse(0)))
        .map(field -> new SelectOption(field.getName(), field.getLabel()))
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(options);
  }

  /**
   * 获取指定字段的参数配置JsonSchema
   *
   * @param fieldName
   * @return
   */
  @Operation(summary = "获取字段参数 Schema", description = "根据字段类型名称获取其参数配置 Schema。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回字段参数 Schema"),
      @ApiResponse(responseCode = "404", description = "字段类型不存在")
  })
  @GetMapping("/field-types/{fieldName}/params-schema")
  public WebApiResult<?> getFieldParamsSchema(
      @Parameter(description = "字段类型名称", required = true)
      @PathVariable("fieldName") String fieldName) {
    var paramsSchema = fieldTypeCatalog.getMetadata(FieldTypeCatalogContext.runtime(), fieldName)
        .map(metadata -> metadata.getParamsUiSchema());
    return WebApiResult.of(404, "字段类型不存在", paramsSchema);
  }

  /**
   * 验证字段名
   */
  @Operation(summary = "验证字段名", description = "校验动态表单字段名是否可用。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "字段名校验通过"),
      @ApiResponse(responseCode = "422", description = "字段名校验失败")
  })
  @PostMapping("/validate-field-name")
  public WebApiResult<?> validateFieldName(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "字段名校验请求", required = true)
      @RequestBody Map<String, String> nameKV) {
    final var blackList = Arrays.asList("id", "createdat", "createdby", "updatedat", "updatedby", "created_at", "created_by", "updated_at", "updated_by");
    var name = Optional.ofNullable(nameKV).map(kv -> kv.get("name")).orElse(null);
    if (StringUtils.isBlank(name)) {
      var result = WebApiResult.error(422, null);
      result.setExtra("errors", "字段名不能为空");
      return result;
    }
    if (blackList.contains(name.toLowerCase())) {
      var result = WebApiResult.error(422, null);
      result.setExtra("errors", "字段名不能为id, createdAt, createdBy, updatedAt, updatedBy");
      return result;
    }
    return WebApiResult.ok();
  }

  /**
   * 刷新表单模型列表
   *
   * @return
   */
  @Operation(summary = "刷新动态表单模型", description = "刷新动态表单模型列表和运行时缓存。", tags = {"dynamic-form"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单模型刷新成功")
  })
  @PostMapping("/refresh")
  public WebApiResult<Boolean> refresh() {
    dynamicFormDefineService.refreshModels();
    return WebApiResult.ok(true);
  }

  private String getUser() {
    var context = SecurityContextHolder.getContext();
    var authentication = Optional.ofNullable(context.getAuthentication());
    var principal = authentication.map(auth -> auth.getPrincipal());
    return principal.map(Object::toString).orElse("anonymous");
  }

  private <T> WebApiResult<T> notFound(String message) {
    return WebApiResult.error(404, message);
  }

  private <T> WebApiResult<T> badRequest(String message) {
    return WebApiResult.error(400, message);
  }
}
