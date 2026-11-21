package com.ouroboros.dynamicform.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.dynamicform.service.DynamicFormDataService;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.util.Dates;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.RequestUtil;
import com.ouroboros.web.WebApiResult;

/**
 * 动态表单数据控制器
 */
@RestController
@RequestMapping("/api/dynamic-form/{name}/data")
public class DynamicFormDataController {

  @Autowired
  DynamicFormDataService dynamicFormDataService;

  @Autowired
  DynamicFormDefineService dynamicFormDefineService;

  /**
   * 根据表单名称获取表单数据
   *
   * @return
   */
  @Operation(
      summary = "分页查询动态表单数据",
      description = "根据动态表单名称和查询参数分页查询表单数据。",
      tags = {"dynamic-form"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回动态表单数据分页结果"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不支持查询"),
      @ApiResponse(responseCode = "404", description = "动态表单不存在")
  })
  @GetMapping
  public PagedWebApiResult<Object> search(
      @Parameter(description = "动态表单名称", required = true)
      @PathVariable("name") String name,
      @Parameter(name = "params", description = "分页、排序与筛选参数")
      @RequestParam Map<String, Object> params) {
    Optional<WebApiResult<Map<String, Object>>> guardResult = guardDynamicFormStatus(name, "已部署", "已归档");
    if (guardResult.isPresent()) {
      return PagedWebApiResult.fail(guardResult.get().getCode(), guardResult.get().getMsg());
    }

    Optional<DataModel> dataModel = dynamicFormDataService.getDataModel(name);
    var result = dataModel.map(model -> {
      var pagination = RequestUtil.getSkipLimit(params);
      var order = RequestUtil.getOrder(params);
      var where = RequestUtil.getWhere(params);
      // 先查询总数
      var countResult = model.count(where);
      if (!countResult.isSuccess()) {
        Throwable cause = countResult.getCause();
        return PagedWebApiResult.fail(cause.getMessage());
      }
      long total = countResult.get();
      // 如果为0直接返回空
      if (total == 0) {
        return PagedWebApiResult.ok(0, Collections.emptyList());
      }
      // 否则查询数据
      var queryResult = model.query(null, where, (Map<String, Object>) (Map<?, ?>) order, pagination._1(), pagination._2());
      if (!queryResult.isSuccess()) {
        Throwable cause = queryResult.getCause();
        return PagedWebApiResult.fail(-1, cause.getMessage());
      }
      return PagedWebApiResult.ok(total, queryResult.get());
    }).orElseGet(() -> PagedWebApiResult.error(404, "表单不存在"));
    return (PagedWebApiResult<Object>) result;
  }


  @Operation(
      summary = "获取动态表单数据详情",
      description = "根据动态表单名称和数据 ID 获取单条表单数据。",
      tags = {"dynamic-form"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回动态表单数据"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不支持查看"),
      @ApiResponse(responseCode = "404", description = "动态表单或数据不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<Map<String, Object>> get(
      @Parameter(description = "动态表单名称", required = true)
      @PathVariable("name") String name,
      @Parameter(description = "动态表单数据 ID", required = true)
      @PathVariable("id") String id) {
    Optional<WebApiResult<Map<String, Object>>> guardResult = guardDynamicFormStatus(name, "已部署", "已归档");
    if (guardResult.isPresent()) {
      return guardResult.get();
    }

    Optional<DataModel> dataModel = dynamicFormDataService.getDataModel(name);
    if (!dataModel.isPresent()) {
      return notFound("表单不存在");
    }

    var result = dataModel.get().get(id);
    if (!result.isSuccess()) {
      Throwable cause = result.getCause();
      return WebApiResult.error(cause.getMessage());
    }
    if (result.get() == null) {
      return notFound("数据不存在");
    }
    return WebApiResult.ok(result.get());
  }

  /**
   * 添加数据
   *
   * @return
   */
  @Operation(
      summary = "新增动态表单数据",
      description = "向指定动态表单中新增一条数据记录。",
      tags = {"dynamic-form"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单数据新增成功"),
      @ApiResponse(responseCode = "400", description = "动态表单数据参数不合法或当前表单状态不支持新增"),
      @ApiResponse(responseCode = "404", description = "动态表单不存在")
  })
  @PostMapping
  public WebApiResult<Map<String, Object>> add(
      @Parameter(description = "动态表单名称", required = true)
      @PathVariable("name") String name,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "动态表单数据内容", required = true)
      @RequestBody Map<String, Object> data) {
    Optional<WebApiResult<Map<String, Object>>> guardResult = guardDynamicFormStatus(name, "已部署");
    if (guardResult.isPresent()) {
      return guardResult.get();
    }

    Optional<DataModel> dataModel = dynamicFormDataService.getDataModel(name);
    if (!dataModel.isPresent()) {
      return notFound("表单不存在");
    }

    data.put("createdAt", Dates.now());
    data.put("createdBy", getUser());
    data.put("updatedAt", Dates.now());
    data.put("updatedBy", getUser());
    data.remove("id");

    var result = dataModel.get().insert(data);
    if (!result.isSuccess()) {
      Throwable cause = result.getCause();
      return WebApiResult.error(cause.getMessage());
    }
    return WebApiResult.ok(result.get());
  }

  /**
   * 更新数据
   *
   * @param name
   * @param id
   * @param data
   * @return
   */
  @Operation(
      summary = "更新动态表单数据",
      description = "根据动态表单名称和数据 ID 更新一条表单数据。",
      tags = {"dynamic-form"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单数据更新成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不支持修改"),
      @ApiResponse(responseCode = "404", description = "动态表单或数据不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<Boolean> update(
      @Parameter(description = "动态表单名称", required = true)
      @PathVariable("name") String name,
      @Parameter(description = "动态表单数据 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的动态表单数据", required = true)
      @RequestBody Map<String, Object> data) {
    Optional<WebApiResult<Boolean>> guardResult = guardDynamicFormStatus(name, "已部署");
    if (guardResult.isPresent()) {
      return guardResult.get();
    }

    Optional<DataModel> dataModel = dynamicFormDataService.getDataModel(name);
    if (!dataModel.isPresent()) {
      return notFound("表单不存在");
    }

    data.put("updatedAt", Dates.now());
    data.put("updatedBy", getUser());
    data.remove("createdAt");
    data.remove("createdBy");
    data.remove("id");

    var result = dataModel.get().update(id, data);
    if (!result.isSuccess()) {
      Throwable cause = result.getCause();
      return WebApiResult.error(cause.getMessage());
    }
    if (result.get() <= 0) {
      return notFound("数据不存在");
    }
    return WebApiResult.ok(true);
  }


  @Operation(
      summary = "删除动态表单数据",
      description = "根据动态表单名称和数据 ID 删除一条表单数据。",
      tags = {"dynamic-form"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单数据删除成功"),
      @ApiResponse(responseCode = "400", description = "动态表单当前状态不支持删除"),
      @ApiResponse(responseCode = "404", description = "动态表单或数据不存在")
  })
  @DeleteMapping("/{id}")
  public WebApiResult<Boolean> delete(
      @Parameter(description = "动态表单名称", required = true)
      @PathVariable("name") String name,
      @Parameter(description = "动态表单数据 ID", required = true)
      @PathVariable("id") String id) {
    Optional<WebApiResult<Boolean>> guardResult = guardDynamicFormStatus(name, "已部署");
    if (guardResult.isPresent()) {
      return guardResult.get();
    }

    Optional<DataModel> dataModel = dynamicFormDataService.getDataModel(name);
    if (!dataModel.isPresent()) {
      return notFound("表单不存在");
    }

    var result = dataModel.get().delete(id);
    if (!result.isSuccess()) {
      Throwable cause = result.getCause();
      return WebApiResult.error(cause.getMessage());
    }
    if (result.get() <= 0) {
      return notFound("数据不存在");
    }
    return WebApiResult.ok(true);
  }

  /**
   * 导入数据
   *
   * @return
   */
  @Operation(
      summary = "批量导入动态表单数据",
      description = "向指定动态表单批量导入多条数据记录。",
      tags = {"dynamic-form"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "动态表单数据导入成功"),
      @ApiResponse(responseCode = "400", description = "导入数据格式错误或当前表单状态不支持导入"),
      @ApiResponse(responseCode = "404", description = "动态表单不存在")
  })
  @PostMapping("/import")
  public WebApiResult<Boolean> importData(
      @Parameter(description = "动态表单名称", required = true)
      @PathVariable("name") String name,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "导入数据请求", required = true)
      @RequestBody Map<String, Object> data) {
    Optional<WebApiResult<Boolean>> guardResult = guardDynamicFormStatus(name, "已部署");
    if (guardResult.isPresent()) {
      return guardResult.get();
    }

    if (!(data.get("items") instanceof List items && items.stream().allMatch(Map.class::isInstance))) {
      return badRequest("数据格式错误");
    }

    var form = dynamicFormDefineService.getDynamicFormByName(name);
    var dataModel = dynamicFormDataService.getDataModel(name);

    // 卫语句
    if (!form.isPresent()) {
      return notFound("表单定义不存在");
    }
    if (!dataModel.isPresent()) {
      return notFound("表单不存在");
    }

    // 准备审计字段
    var createdAndUpdatedInfo = new LinkedHashMap<String, Object>();
    createdAndUpdatedInfo.put("createdAt", Dates.now());
    createdAndUpdatedInfo.put("createdBy", getUser());
    createdAndUpdatedInfo.put("updatedAt", Dates.now());
    createdAndUpdatedInfo.put("updatedBy", getUser());

    var recordsToInsert = (List<Map<String, Object>>) items.stream()
        .map(item -> {
          var map = (Map) item;

          // 将导入的 Label -> Value 数据转换为 Name -> Value 数据
          var record = form.get().getFields().stream()
              .filter(field -> !Objects.equals(field.getName(), "id") && !createdAndUpdatedInfo.containsKey(field.getName()))
              .collect(() -> new LinkedHashMap<String, Object>(), (m, field) -> {
                var value = map.get(field.getLabel());
                m.put(field.getName(), value);
              }, Map::putAll);

          // 将审计字段并入
          record.putAll(createdAndUpdatedInfo);
          record.remove("id");
          return record;
        })
        .collect(Collectors.toList());

    // 批量插入
    var result = dataModel.get().batchInsert(recordsToInsert);
    if (!result.isSuccess()) {
      Throwable cause = result.getCause();
      return WebApiResult.error(cause.getMessage());
    }

    return WebApiResult.ok(true);
  }

  public <T> Optional<WebApiResult<T>> guardDynamicFormStatus(String name, String... statuses) {
    return dynamicFormDataService.validateStatus(name, statuses)
        .map(failure -> WebApiResult.error(failure.code(), failure.message()));
  }

  private <T> WebApiResult<T> notFound(String message) {
    return WebApiResult.error(404, message);
  }

  private <T> WebApiResult<T> badRequest(String message) {
    return WebApiResult.error(400, message);
  }

  private String getUser() {
    var context = SecurityContextHolder.getContext();
    var authentication = Optional.ofNullable(context.getAuthentication());
    var principal = authentication.map(auth -> auth.getPrincipal());
    return principal.map(Object::toString).orElse("anonymous");
  }
}
