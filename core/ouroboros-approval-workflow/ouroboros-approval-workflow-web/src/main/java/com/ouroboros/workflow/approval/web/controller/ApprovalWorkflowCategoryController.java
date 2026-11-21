package com.ouroboros.workflow.approval.web.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.workflow.approval.manage.entity.WorkflowCategory;
import com.ouroboros.workflow.approval.manage.service.ModelCategoryService;
import com.ouroboros.workflow.approval.web.controller.base.BaseController;

/**
 * @author liansz
 */

@RestController
@RequestMapping("/api/approval-workflow/category")
public class ApprovalWorkflowCategoryController
    extends BaseController<String, WorkflowCategory, WorkflowCategory, ModelCategoryService> {

  @Operation(
      summary = "分页查询流程分类",
      description = "按关键字分页查询审批流程分类。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程分类分页结果")
  })
  @GetMapping
  public PagedWebApiResult<? extends WorkflowCategory> search(
      @Parameter(description = "分类关键字") @RequestParam(required = false) String keyword,
      @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") int page,
      @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "20") int perPage) {
    Map<String, Object> where = new HashMap<>();
    if (ObjectUtils.isNotEmpty(keyword)) {
      where.put("name", Collections.singletonMap("like", "%" + keyword + "%"));
    }

    Map<String, Object> statement = new HashMap<>();
    statement.put("where", where);

    return PagedWebApiResult.ok(pageSearch(statement, page, perPage));
  }
}
