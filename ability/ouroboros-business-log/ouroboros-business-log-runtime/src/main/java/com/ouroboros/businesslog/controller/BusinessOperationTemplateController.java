package com.ouroboros.businesslog.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.businesslog.dto.request.CreateBusinessOperationTemplateOverrideReq;
import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.businesslog.service.BusinessOperationTemplateQueryService;
import com.ouroboros.businesslog.service.BusinessOperationTemplateService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 **/
@RestController
@RequestMapping("/api/sys/business-log-template")
public class BusinessOperationTemplateController {

  @Autowired
  private BusinessOperationTemplateService service;

  @Autowired
  private BusinessOperationTemplateQueryService queryService;

  @Operation(
      summary = "创建业务日志模板",
      description = "创建新的业务操作日志模板。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "日志模板创建成功"),
      @ApiResponse(responseCode = "400", description = "日志模板参数不合法")
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WebApiResult<BusinessOperationTemplateModel> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "日志模板定义", required = true)
      @RequestBody BusinessOperationTemplateModel model) {
    return WebApiResult.ok(service.add(model));
  }

  @Operation(
      summary = "删除业务日志模板",
      description = "根据模板 ID 删除业务操作日志模板。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "日志模板删除成功"),
      @ApiResponse(responseCode = "404", description = "日志模板不存在")
  })
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @Parameter(description = "日志模板 ID", required = true)
      @PathVariable("id") String id) {
    Boolean status = service.delete(id);
    if (!status) {
      throw new BusinessException(404, "日志模板不存在");
    }
  }

  @Operation(
      summary = "更新业务日志模板",
      description = "根据模板 ID 更新业务操作日志模板。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "日志模板更新成功"),
      @ApiResponse(responseCode = "404", description = "日志模板不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<BusinessOperationTemplateModel> update(
      @Parameter(description = "日志模板 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的日志模板定义", required = true)
      @RequestBody BusinessOperationTemplateModel model) {
    if (!service.update(id, model)) {
      throw new BusinessException(404, "日志模板不存在");
    }
    return WebApiResult.of(404, "日志模板不存在", service.getById(id));
  }

  @Operation(
      summary = "分页查询业务日志模板",
      description = "按关键字、来源、请求方法和模块名称过滤查询业务日志模板。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回日志模板分页结果")
  })
  @GetMapping
  public PagedWebApiResult<?> search(
      @Parameter(description = "模板关键字")
      @RequestParam(value = "keyword", required = false) String keyword,
      @Parameter(description = "模板来源")
      @RequestParam(value = "source", required = false) String source,
      @Parameter(description = "HTTP 方法")
      @RequestParam(value = "httpMethod", required = false) String httpMethod,
      @Parameter(description = "模块名称")
      @RequestParam(value = "moduleName", required = false) String moduleName,
      @Parameter(description = "页码")
      @RequestParam(value = "page", required = false, defaultValue = "1") int page,
      @Parameter(description = "每页条数")
      @RequestParam(value = "perPage", required = false, defaultValue = "20") int perPage) {
    return PagedWebApiResult.ok(queryService.search(keyword, source, httpMethod, moduleName, page, perPage));
  }

  @Operation(
      summary = "获取业务日志模板详情",
      description = "根据模板 key 查询业务日志模板详情。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回日志模板详情"),
      @ApiResponse(responseCode = "404", description = "日志模板不存在")
  })
  @GetMapping("/detail")
  public WebApiResult<?> getDetail(
      @Parameter(description = "日志模板 key", required = true)
      @RequestParam("templateKey") String templateKey) {
    return WebApiResult.of(404, "日志模板不存在", queryService.getDetail(templateKey));
  }

  @Operation(
      summary = "从来源生成覆盖模板",
      description = "根据现有来源模板生成一份可编辑的业务日志覆盖模板。",
      tags = {"business-log"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "覆盖模板生成成功"),
      @ApiResponse(responseCode = "404", description = "来源模板不存在")
  })
  @PostMapping("/override-from-source")
  public WebApiResult<BusinessOperationTemplateModel> createOverrideFromSource(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "来源模板覆盖请求", required = true)
      @RequestBody CreateBusinessOperationTemplateOverrideReq req) {
    return WebApiResult.of(
        404,
        "来源模板不存在",
        java.util.Optional.ofNullable(queryService.buildOverrideFromSource(req.getTemplateKey()))
    );
  }
}
