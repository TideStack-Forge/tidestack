package com.ouroboros.workflow.approval.web.controller;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.workflow.approval.CandidateProvider;

@RestController
@RequestMapping("/api/approval-workflow/candidate")
public class ApprovalWorkflowCandidateController {
  final Map<String, CandidateProvider> candidateTypeMap = new HashMap<>();
  final List<CandidateProvider> candidateProviderList;

  @Autowired
  public ApprovalWorkflowCandidateController(List<CandidateProvider> candidateProviders) {
    this.candidateProviderList = candidateProviders;
    candidateProviders.forEach(t -> this.candidateTypeMap.put(t.getName(), t));
  }

  // TODO: 考虑增加流程类型参数，以实现更复杂的共存关系
  @Operation(
      summary = "获取候选人类型列表",
      description = "返回审批工作流支持的候选人提供方列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回候选人类型列表")
  })
  @GetMapping("")
  public ItemsWebApiResult<CandidateProvider> getCandidateTypes(
      @Parameter(description = "开发态应用名称") @RequestParam(required = false) String appName) {
    return ItemsWebApiResult.ok(runWithAppName(appName, () -> candidateProviderList));
  }

  @Operation(
      summary = "查询候选人条目",
      description = "按候选人类型、分类和关键字查询候选人条目。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回候选人条目列表")
  })
  @GetMapping("/{type}/items")
  public ItemsWebApiResult<CandidateProvider.CandidateItem> searchItems(
      @Parameter(description = "候选人类型", required = true) @PathVariable String type,
      @Parameter(description = "候选人分类") @RequestParam(required = false) String category,
      @Parameter(description = "候选人关键字") @RequestParam(required = false) String keyword,
      @Parameter(description = "候选人值列表") @RequestParam(required = false, name = "value[]") List<String> values,
      @Parameter(description = "开发态应用名称") @RequestParam(required = false) String appName) {
    var items = runWithAppName(appName, () -> Optional.ofNullable(candidateTypeMap.get(type))
        .map(t -> values != null ? t.search(values) : t.search(category, keyword))
        .orElseGet(Collections::emptyList));
    return ItemsWebApiResult.ok(items);
  }

  @Operation(
      summary = "获取候选人分类",
      description = "根据候选人类型返回其支持的分类列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回候选人分类")
  })
  @GetMapping("/{type}/category")
  public ItemsWebApiResult<CandidateProvider.CandidateCategory> getCategories(
      @Parameter(description = "候选人类型", required = true) @PathVariable String type,
      @Parameter(description = "开发态应用名称") @RequestParam(required = false) String appName) {
    var categories = runWithAppName(appName, () -> Optional.ofNullable(candidateTypeMap.get(type))
        .map(CandidateProvider::getCategories)
        .orElseGet(Collections::emptyList));
    return ItemsWebApiResult.ok(categories);
  }

  private <T> T runWithAppName(String appName, java.util.function.Supplier<T> supplier) {
    if (appName == null || appName.trim().isEmpty()) {
      return supplier.get();
    }
    return DevAppHolder.runWithAppName(appName, supplier);
  }
}
