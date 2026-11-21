package com.ouroboros.workflow.approval.runtime.controller;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.dynamicform.service.DynamicFormWorkflowRecordService;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowBinding;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowHistory;
import com.ouroboros.workflow.approval.model.FlowTimeline;

@RestController
@ConditionalOnBean(DynamicFormWorkflowRecordService.class)
@RequestMapping("/api/approval-workflow/dynamic-form")
public class ApprovalWorkflowDynamicFormRecordController {
  private final DynamicFormWorkflowRecordService recordService;
  private final FlowInstanceService flowInstanceService;

  public ApprovalWorkflowDynamicFormRecordController(DynamicFormWorkflowRecordService recordService,
                                                     FlowInstanceService flowInstanceService) {
    this.recordService = recordService;
    this.flowInstanceService = flowInstanceService;
  }

  @Operation(
      summary = "查询动态表单流程绑定",
      description = "按流程实例、动态表单和表单数据记录查询审批流绑定关系。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回动态表单流程绑定"),
      @ApiResponse(responseCode = "400", description = "缺少流程实例范围"),
      @ApiResponse(responseCode = "403", description = "当前用户无权查看该流程实例")
  })
  @GetMapping("/bindings")
  public PagedWebApiResult<DynamicFormWorkflowBinding> listBindings(
      @Parameter(description = "流程实例 ID", required = true) @RequestParam String instanceId,
      @Parameter(description = "动态表单标识") @RequestParam(required = false) String formKey,
      @Parameter(description = "动态表单数据 ID") @RequestParam(required = false) String formId,
      @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Integer page,
      @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "20") Integer perPage) {
    var failure = authorizeInstance(instanceId);
    if (failure.isPresent()) {
      return PagedWebApiResult.fail(failure.get().code(), failure.get().message());
    }
    Long total = recordService.countBindings(instanceId, formKey, formId);
    if (total == 0) {
      return PagedWebApiResult.ok(total, Collections.emptyList());
    }
    return PagedWebApiResult.ok(total, recordService.listBindings(
        instanceId,
        formKey,
        formId,
        offset(page, perPage),
        perPage));
  }

  @Operation(
      summary = "查询动态表单流程历史",
      description = "按流程实例、任务、动态表单和表单数据记录查询审批流表单变更历史。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回动态表单流程历史"),
      @ApiResponse(responseCode = "400", description = "缺少流程实例范围"),
      @ApiResponse(responseCode = "403", description = "当前用户无权查看该流程实例")
  })
  @GetMapping("/history")
  public PagedWebApiResult<DynamicFormWorkflowHistory> listHistory(
      @Parameter(description = "流程实例 ID", required = true) @RequestParam String instanceId,
      @Parameter(description = "任务 ID") @RequestParam(required = false) String taskId,
      @Parameter(description = "动态表单标识") @RequestParam(required = false) String formKey,
      @Parameter(description = "动态表单数据 ID") @RequestParam(required = false) String formId,
      @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Integer page,
      @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "20") Integer perPage) {
    var failure = authorizeInstance(instanceId);
    if (failure.isPresent()) {
      return PagedWebApiResult.fail(failure.get().code(), failure.get().message());
    }
    Long total = recordService.countHistory(instanceId, taskId, formKey, formId);
    if (total == 0) {
      return PagedWebApiResult.ok(total, Collections.emptyList());
    }
    return PagedWebApiResult.ok(total, recordService.listHistory(
        instanceId,
        taskId,
        formKey,
        formId,
        offset(page, perPage),
        perPage));
  }

  private Optional<QueryFailure> authorizeInstance(String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      return Optional.of(new QueryFailure(400, "查询动态表单流程记录必须提供流程实例ID"));
    }
    String currentUserId = currentUserId();
    List<FlowTimeline> timelines = flowInstanceService.getTimeLine(instanceId);
    boolean allowed = timelines.stream()
        .filter(Objects::nonNull)
        .flatMap(timeline -> Optional.ofNullable(timeline.getTaskInstances())
            .orElseGet(Collections::emptyList)
            .stream())
        .flatMap(task -> Stream.concat(
            Stream.of(task.getAssignee()),
            Optional.ofNullable(task.getCandidateUsers()).orElseGet(Collections::emptyList).stream()))
        .filter(Objects::nonNull)
        .anyMatch(currentUserId::equals);
    return allowed ? Optional.empty() : Optional.of(new QueryFailure(403, "当前用户无权查看该流程实例"));
  }

  private int offset(Integer page, Integer perPage) {
    int normalizedPage = page == null || page < 1 ? 1 : page;
    int normalizedPerPage = perPage == null || perPage < 1 ? 20 : perPage;
    return (normalizedPage - 1) * normalizedPerPage;
  }

  protected String currentUserId() {
    return CurrentUserSupplier.getCurrentUserId().orElse("anonymousUser");
  }

  private record QueryFailure(int code, String message) {
  }
}
