package com.ouroboros.uischema.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.model.CustomUISchema;
import com.ouroboros.uischema.service.CustomUISchemaService;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.RequestUtil;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/custom-ui-schema")
public class CustomUISchemaController {

  @Autowired
  private CustomUISchemaService service;

  @Autowired
  private TypedDataModel<String, CustomUISchema> dataModel;

  @Operation(summary = "创建自定义界面 Schema", description = "新增一个自定义界面 Schema。", tags = {"ui-schema"})
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "自定义界面 Schema 创建成功"),
      @ApiResponse(responseCode = "400", description = "界面 Schema 参数不合法")
  })
  @PostMapping("")
  @ResponseStatus(HttpStatus.CREATED)
  public WebApiResult<CustomUISchema> create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "自定义界面 Schema 定义", required = true)
      @RequestBody CustomUISchema item) throws Exception {
    return WebApiResult.ok(service.add(item, getUser()));
  }

  @Operation(summary = "删除自定义界面 Schema", description = "根据 ID 删除自定义界面 Schema。", tags = {"ui-schema"})
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "自定义界面 Schema 删除成功"),
      @ApiResponse(responseCode = "404", description = "自定义界面 Schema 不存在")
  })
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @Parameter(description = "自定义界面 Schema ID", required = true)
      @PathVariable("id") String id) throws Exception {
    Boolean status = service.delete(id, getUser());
    if (!status) {
      throw new BusinessException(404, "自定义界面 Schema 不存在");
    }
  }

  @Operation(summary = "更新自定义界面 Schema", description = "根据 ID 更新自定义界面 Schema。", tags = {"ui-schema"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "自定义界面 Schema 更新成功"),
      @ApiResponse(responseCode = "404", description = "自定义界面 Schema 不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<CustomUISchema> update(
      @Parameter(description = "自定义界面 Schema ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的自定义界面 Schema 定义", required = true)
      @RequestBody CustomUISchema item) throws Exception {
    if (!service.update(id, item, getUser())) {
      throw new BusinessException(404, "自定义界面 Schema 不存在");
    }
    return WebApiResult.of(404, "自定义界面 Schema 不存在", Optional.ofNullable(service.getById(id)));
  }

  @Operation(summary = "获取自定义界面 Schema 详情", description = "根据 ID 查询自定义界面 Schema。", tags = {"ui-schema"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回自定义界面 Schema"),
      @ApiResponse(responseCode = "404", description = "自定义界面 Schema 不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<CustomUISchema> get(
      @Parameter(description = "自定义界面 Schema ID", required = true)
      @PathVariable("id") String id) throws Exception {
    return WebApiResult.of(404, "自定义界面 Schema 不存在", Optional.ofNullable(service.getById(id)));
  }

  @Operation(summary = "分页查询自定义界面 Schema", description = "根据筛选条件分页查询自定义界面 Schema。", tags = {"ui-schema"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回自定义界面 Schema 分页结果")
  })
  @GetMapping("")
  public PagedWebApiResult<CustomUISchema> search(
      @Parameter(name = "params", description = "分页、排序与筛选参数")
      @RequestParam Map<String, Object> params) throws Exception {
    var pagination = RequestUtil.getSkipLimit(params);
    var order = RequestUtil.getOrder(params);
    var where = RequestUtil.getWhere(params);
    var builder = getDataModel().query()
        .where(where)
        .clause("OFFSET", pagination._1())
        .clause("LIMIT", pagination._2());
    if (order.size() == 1) {
      var orderField = order.keySet().stream().findFirst().get();
      var orderDir = order.get(orderField);
      builder.clause("ORDER", Map.of(orderField, orderDir));
    }

    var countResult = getDataModel().count(where);
    if (countResult.isFailure()) {
      throw new PlatformException("查询数量失败", countResult.getCause());
    }
    var total = countResult.get();

    var queryResult = builder.execute();
    if (queryResult.isFailure()) {
      throw new PlatformException("查询数据失败", queryResult.getCause());
    }
    var items = queryResult.get();

    return PagedWebApiResult.ok(total, items);
  }

  private TypedDataModel<String, CustomUISchema> getDataModel() {
    return dataModel;
  }

  private String getUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .map(Object::toString)
        .orElse("anonymous");
  }
}
