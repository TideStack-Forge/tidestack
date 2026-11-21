package com.ouroboros.workflow.approval.web.controller;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.web.AbstractWebApiResult;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.manage.service.ModelCategoryService;
import com.ouroboros.workflow.approval.model.FlowForm;

@RestController
@RequestMapping("/api/approval-workflow/base-info")
public class ApprovalWorkflowBaseInfoController {

  final Map<String, FlowFormProvider> flowFormProviderMap = new HashMap<>();
  final List<FlowFormProvider> flowFormProviderList;

  @Autowired
  ModelCategoryService categoryService;

  public ApprovalWorkflowBaseInfoController(List<FlowFormProvider> flowFormProviderList) {
    this.flowFormProviderList = flowFormProviderList;
    flowFormProviderList.forEach(t -> this.flowFormProviderMap.put(t.getType(), t));
  }

  @GetMapping("/form-types")
  public ItemsWebApiResult<Map<String, String>> formTypes(@RequestParam(required = false) String value) {
    var options = new ArrayList<>(flowFormProviderList.stream()
        .map(provider -> Map.of("value", provider.getType(), "label", provider.getTitle())).toList());
    if (value != null && !value.isBlank() && !flowFormProviderMap.containsKey(value)) {
      options.add(Map.of("value", value, "label", value + "（当前来源不可用）"));
    }
    return ItemsWebApiResult.ok(options);
  }

  @Operation(
      summary = "获取流程分类列表",
      description = "返回审批流程可用的分类列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程分类列表")
  })
  @GetMapping("/categories")
  public ItemsWebApiResult<?> categories() {
    return ItemsWebApiResult.ok(
        categoryService.searchAll()
            .stream()
                    .map(model -> Collections.singletonMap("name", model.getName()))
                    .collect(Collectors.toList())
    );
  }

  @Operation(
      summary = "查询流程表单",
      description = "按类型、关键字和名称查询审批流程表单。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程表单结果")
  })
  @GetMapping("/form/{type}")
  public Object searchForm(
      @Parameter(description = "表单类型", required = true) @PathVariable String type,
      @Parameter(description = "表单关键字") @RequestParam(required = false) String keyword,
      @Parameter(description = "表单名称") @RequestParam(required = false, name = "value") String name,
      @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Integer page,
      @Parameter(description = "每页条数") @RequestParam(required = false) Integer perPage) {
    Function<Tuple2<Long, List<FlowForm>>, AbstractWebApiResult> buildResult =
        t -> perPage == null ? ItemsWebApiResult.ok(t._2) : PagedWebApiResult.ok(t._1, t._2);

    var formProvider = flowFormProviderMap.get(type);
    if (formProvider == null) {
      return buildResult.apply(Tuple.of(0L, Collections.emptyList()));
    }

    if (ObjectUtils.isNotEmpty(name)) {
      return formProvider.get(name)
          .map(Collections::singletonList)
          .map(l -> buildResult.apply(Tuple.of(1L, l)))
          .orElseGet(() -> buildResult.apply(Tuple.of(0L, Collections.emptyList())));
    }

    return buildResult.apply(formProvider.search(keyword, page, perPage != null ? perPage : Integer.MAX_VALUE));
  }

  @Operation(
      summary = "获取审批流表单字段来源",
      description = "根据表单类型和名称返回审批流编辑器统一字段来源。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回审批流表单字段来源")
  })
  @GetMapping("/form/{type}/{name}/workflow-field")
  public WebApiResult<Map<String, Object>> getWorkflowFields(
      @Parameter(description = "表单类型", required = true) @PathVariable String type,
      @Parameter(description = "表单名称", required = true) @PathVariable String name) {
    var formProvider = flowFormProviderMap.get(type);
    if (formProvider == null) {
      return WebApiResult.ok(new com.ouroboros.workflow.approval.form.FlowFormSourceAssembler().build(type, name, null));
    }
    var form = formProvider.get(name);
    return form
        .map(value -> new com.ouroboros.workflow.approval.form.FlowFormSourceAssembler().build(type, name, value))
        .map(WebApiResult::ok)
        .orElseGet(() -> WebApiResult.error("表单不存在"));
  }

}
