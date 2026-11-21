package com.ouroboros.businesslog.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.businesslog.model.BusinessLogModel;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 **/
@RestController
@RequestMapping("/api/sys/business-log")
public class BusinessOperationController {

  @Autowired
  private TypedDataModel<String, BusinessLogModel> dataModel;

  @Operation(
      summary = "获取业务日志详情",
      description = "根据日志 ID 查询业务操作日志详情。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回日志详情"),
      @ApiResponse(responseCode = "404", description = "业务日志不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<?> get(
      @Parameter(description = "业务日志 ID", required = true)
      @PathVariable("id") String id) {
    Map<String, Object> statement = new HashMap<>();
    statement.put("populate", "employee");

    return WebApiResult.of(
        404,
        "业务日志不存在",
        Optional.ofNullable(dataModel.get(id, statement).getOrElse(() -> null))
    );
  }

  @Operation(
      summary = "分页查询业务日志",
      description = "按模块、操作类型、操作人和业务主键等条件分页查询业务日志。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回业务日志分页结果")
  })
  @GetMapping
  public PagedWebApiResult<?> search(
      @Parameter(description = "模块名称前缀")
      @RequestParam(value = "moduleName", required = false) String moduleName,
      @Parameter(description = "操作名称")
      @RequestParam(value = "operation", required = false) String operation,
      @Parameter(description = "操作人标识")
      @RequestParam(value = "operator", required = false) String operator,
      @Parameter(description = "业务主键")
      @RequestParam(value = "businessKey", required = false) String businessKey,
      @Parameter(description = "页码")
      @RequestParam(value = "page", required = false, defaultValue = "1") int page,
      @Parameter(description = "每页条数")
      @RequestParam(value = "perPage", required = false, defaultValue = "20") int perPage) {
    Map<String, Object> where = new HashMap<>();
    if (StringUtils.isNotEmpty(moduleName)) {
      where.put("moduleName", Collections.singletonMap("like", moduleName + "%"));
    }

    if (StringUtils.isNotEmpty(operation)) {
      where.put("operation", Collections.singletonMap("like", operation));
    }

    if (StringUtils.isNotEmpty(operator)) {
      where.put("operator", Collections.singletonMap("like", operator));
    }

    if (StringUtils.isNotEmpty(businessKey)) {
      where.put("businessKey", businessKey);
    }

    Map<String, Object> statement = new HashMap<>();
    statement.put("omit", Arrays.asList("payload", "responseBody"));
    statement.put("where", where);
    statement.put("skip", (page - 1) * perPage);
    statement.put("limit", perPage);
    statement.put("order", "operationTime desc");

    Long total = dataModel.count(where).getOrElse(0L);
    return PagedWebApiResult.ok(
        total,
        dataModel.query(statement).getOrElse(Collections.emptyList())
    );
  }
}
