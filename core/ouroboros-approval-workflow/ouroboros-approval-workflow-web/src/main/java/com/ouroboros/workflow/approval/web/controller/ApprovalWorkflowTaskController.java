package com.ouroboros.workflow.approval.web.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.web.service.ApprovalWorkflowTargetQueryService;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;
import com.ouroboros.workflow.approval.web.vo.SubmitTaskVO;
import com.ouroboros.workflow.approval.web.vo.TargetItemVO;

@RestController
@RequestMapping("/api/approval-workflow/task")
public class ApprovalWorkflowTaskController {
  private static final String ACTION_TRANSFER = "transfer";
  private static final String ACTION_ADD_SIGN = "addSign";
  private static final String ACTION_FREE_REJECT = "freeReject";
  private static final String ACTION_FREE_RECOVER = "freeRecover";
  private static final String ACTION_FREE_APPROVE = "freeApprove";
  private static final String ACTION_FREE_APPROVAL = "freeApproval";

  private static final Set<String> ADVANCED_ACTIONS = new HashSet<>(Arrays.asList(
      ACTION_TRANSFER,
      ACTION_ADD_SIGN,
      ACTION_FREE_REJECT,
      ACTION_FREE_RECOVER,
      ACTION_FREE_APPROVE,
      ACTION_FREE_APPROVAL));

  private static final Map<String, String> ACTION_BUTTON_REFS;

  static {
    Map<String, String> actionButtonRefs = new HashMap<>();
    actionButtonRefs.put("approve", "approveButton");
    actionButtonRefs.put("reject", "rejectButton");
    actionButtonRefs.put("rollback", "rollbackButton");
    actionButtonRefs.put(ACTION_TRANSFER, "transferButton");
    actionButtonRefs.put(ACTION_ADD_SIGN, "addSignButton");
    actionButtonRefs.put(ACTION_FREE_REJECT, "freeRejectButton");
    actionButtonRefs.put(ACTION_FREE_APPROVE, "freeApproveButton");
    actionButtonRefs.put(ACTION_FREE_APPROVAL, "freeApproveButton");
    ACTION_BUTTON_REFS = Collections.unmodifiableMap(actionButtonRefs);
  }

  @Autowired
  FlowTaskService flowTaskService;

  @Autowired
  WorkflowModelService workflowModelService;

  @Autowired
  UsernameResolverRegistry usernameResolverRegistry;

  @Autowired
  ApprovalWorkflowTargetQueryService approvalWorkflowTargetQueryService;

  @Operation(
      summary = "分页查询待办任务",
      description = "查询当前用户的审批待办任务列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回待办任务列表")
  })
  @GetMapping("/todo")
  public PagedWebApiResult<?> getTodoTasks(
      @Parameter(description = "页码", required = true) @RequestParam int page,
      @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int perPage) {
    String userId = getCurrentUserId();
    Long total = flowTaskService.getTaskCount(userId);
    if (total == 0) {
      return PagedWebApiResult.ok(total, Collections.emptyList());
    }
    List<FlowTaskDetail> items = flowTaskService.getTaskList(userId, (page - 1) * perPage, perPage);

    // 获取发起人名称
    List<String> users = items.stream().map(FlowTaskDetail::getStarter).collect(Collectors.toList());
    Map<String, String> userNameMap = usernameResolverRegistry.resolve(users);

    // 获取流程定义
    List<String> flowKeys = items.stream().map(FlowTaskDetail::getFlowKey).collect(Collectors.toList());
    Map<String, Object> where = new HashMap<>();
    where.put("id", flowKeys);
    Map<String, Object> statement = new HashMap<>();
    statement.put("where", where);
    List<? extends WorkflowModel> workflowModels = workflowModelService.searchList(statement);

    List<Map<String, Object>> result = items.stream()
        .map(Maps::fromBean)
        .peek(item -> {
          String starterName = Optional.ofNullable(item.get("starter"))
              .filter(Objects::nonNull)
              .map(Object::toString)
              .map(userNameMap::get)
              .orElseGet(() -> item.getOrDefault("starter", "未知用户").toString());
          item.put("starterName", starterName);

          String flowKey = (String) item.get("flowKey");
          String taskDefinitionKey = (String) item.get("taskDefinitionKey");

          if (ObjectUtils.isNotEmpty(flowKey) && ObjectUtils.isNotEmpty(taskDefinitionKey)) {
            workflowModels.stream()
                .filter(model -> Objects.equals(flowKey, model.getId()))
                .findFirst()
                .ifPresent(model -> {
                  Map<String, Object> map = Json.toBean(HashMap.class, model.getJson());
                  if (map == null) {
                    return;
                  }

                  List<Map<String, Object>> nodes = flowDefinitionNodes(map);
                  if (nodes.isEmpty()) {
                    return;
                  }

                  nodes.stream()
                      .filter(node -> Objects.equals(taskDefinitionKey, node.get("id")))
                      .findFirst()
                      .ifPresent(node -> {
                        item.put("type", node.get("type"));
                      });
                });
          }
        })
        .collect(Collectors.toList());
    return PagedWebApiResult.ok(total, result);
  }

  @Operation(
      summary = "分页查询已办任务",
      description = "查询当前用户的审批已办任务列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回已办任务列表")
  })
  @GetMapping("/finished")
  public PagedWebApiResult<FlowTaskDetail> getHistoryTasks(
      @Parameter(description = "页码", required = true) @RequestParam int page,
      @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int perPage) {
    String userId = getCurrentUserId();
    Long total = flowTaskService.getHistoryTaskCount(userId);
    if (total == 0) {
      return PagedWebApiResult.ok(total, Collections.emptyList());
    }
    List<FlowTaskDetail> items = flowTaskService.getHistoryTaskList(userId, (page - 1) * perPage, perPage);
    return PagedWebApiResult.ok(total, items);
  }

  @Operation(
      summary = "获取任务表单 Schema",
      description = "根据任务 ID 获取任务处理表单 Schema。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回任务表单 Schema")
  })
  @GetMapping("/{taskId}/task-form-schema")
  public WebApiResult<SchemaNode> getTaskFormSchema(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.of(flowTaskService.getTaskForm(taskId).map(this::decorateTaskForm)
        .recover(com.ouroboros.workflow.approval.FlowFormServiceUnavailableException.class, unavailable -> {
          SchemaNode guidance = new SchemaNode();
          guidance.setType("alert");
          guidance.setProp("level", "info");
          guidance.setProp("body", unavailable.getMessage());
          return guidance;
        }));
  }

  private SchemaNode decorateTaskForm(SchemaNode taskForm) {
    Object bodyObject = taskForm.getProp("body");
    if (bodyObject instanceof List<?> bodyList) {
      bodyList.stream()
          .filter(SchemaNode.class::isInstance)
          .map(SchemaNode.class::cast)
          .forEach(body -> body.setProp("name", "formData." + body.getProp("name")));
    }

    taskForm.setProp("actions", resolveActionButtons(taskForm.getProp("actions")));
    return taskForm;
  }

  /**
   * 从流程定义 JSON 中安全取出 nodes：{@code Json.toBean(HashMap.class, ...)} 不保证元素类型，
   * 直接强转会在历史/外部数据上抛 ClassCastException（接口 500）。
   */
  private List<Map<String, Object>> flowDefinitionNodes(Map<String, Object> definitionJson) {
    Object nodesObject = definitionJson.get("nodes");
    if (!(nodesObject instanceof List<?> nodesList)) {
      return Collections.emptyList();
    }
    return nodesList.stream()
        .filter(Map.class::isInstance)
        .map(node -> {
          Map<?, ?> raw = (Map<?, ?>) node;
          Map<String, Object> typed = new HashMap<>();
          raw.forEach((key, value) -> typed.put(String.valueOf(key), value));
          return typed;
        })
        .collect(Collectors.toList());
  }

  @Operation(
      summary = "提交任务处理结果",
      description = "对指定审批任务执行审批动作并提交表单数据。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "任务处理成功")
  })
  @PostMapping("/{taskId}")
  public WebApiResult<Void> submitTask(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "任务提交数据", required = true)
      @RequestBody SubmitTaskVO submitTask) {
    String userId = getCurrentUserId();
    String action = submitTask.getAction();
    String comment = submitTask.getComment();
    Map<String, Object> formData = submitTask.getFormData();
    Map<String, Object> payload = resolvePayload(submitTask, action);
    if (isAdvancedAction(action) && payload == null) {
      return WebApiResult.error("高级动作缺少必要参数");
    }

    io.vavr.control.Try<Void> result = flowTaskService.doAction(taskId, action, userId, comment, formData, payload);
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "查询转办可选目标用户",
      description = "返回当前任务可转办到的目标用户列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回转办目标用户列表"),
      @ApiResponse(responseCode = "404", description = "任务不存在")
  })
  @GetMapping("/{taskId}/transfer-targets")
  public WebApiResult<List<TargetItemVO>> getTransferTargets(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.ok(approvalWorkflowTargetQueryService.getTransferTargets(taskId, getCurrentUserId()));
  }

  @Operation(
      summary = "查询加签可选目标用户",
      description = "返回当前任务可加签的目标用户列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回加签目标用户列表"),
      @ApiResponse(responseCode = "404", description = "任务不存在")
  })
  @GetMapping("/{taskId}/add-sign-targets")
  public WebApiResult<List<TargetItemVO>> getAddSignTargets(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.ok(approvalWorkflowTargetQueryService.getAddSignTargets(taskId, getCurrentUserId()));
  }

  @Operation(
      summary = "查询自由驳回可选目标节点",
      description = "返回当前任务可自由驳回到的目标节点列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回自由驳回目标节点列表"),
      @ApiResponse(responseCode = "404", description = "任务不存在")
  })
  @GetMapping("/{taskId}/free-reject-targets")
  public WebApiResult<List<TargetItemVO>> getFreeRejectTargets(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.ok(approvalWorkflowTargetQueryService.getFreeRejectTargets(taskId, getCurrentUserId()));
  }

  @Operation(
      summary = "查询自由审批可选协商用户",
      description = "返回当前任务可自由审批协商的用户列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回自由审批协商用户列表"),
      @ApiResponse(responseCode = "404", description = "任务不存在")
  })
  @GetMapping("/{taskId}/free-approval-targets")
  public WebApiResult<List<TargetItemVO>> getFreeApprovalTargets(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.ok(approvalWorkflowTargetQueryService.getFreeApprovalTargets(taskId, getCurrentUserId()));
  }

  @Operation(
      summary = "查询拒绝可选目标节点",
      description = "返回当前任务可拒绝到的节点列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回拒绝目标节点列表"),
      @ApiResponse(responseCode = "404", description = "任务不存在")
  })
  @GetMapping("/{taskId}/reject-targets")
  public WebApiResult<List<TargetItemVO>> getRejectTargets(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.ok(approvalWorkflowTargetQueryService.getRejectTargets(taskId, getCurrentUserId()));
  }

  @Operation(
      summary = "查询恢复可选目标节点",
      description = "返回当前任务可恢复到的节点列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回恢复目标节点列表"),
      @ApiResponse(responseCode = "404", description = "任务不存在")
  })
  @GetMapping("/{taskId}/recovery-targets")
  public WebApiResult<List<TargetItemVO>> getRecoveryTargets(
      @Parameter(description = "任务 ID", required = true) @PathVariable String taskId) {
    return WebApiResult.ok(approvalWorkflowTargetQueryService.getRecoveryTargets(taskId, getCurrentUserId()));
  }

  private String getCurrentUserId() {
    return CurrentUserSupplier.getCurrentUserId()
        .orElse("anonymousUser");
  }

  private List<Map<String, String>> resolveActionButtons(Object actionsObject) {
    if (!(actionsObject instanceof List)) {
      return Collections.emptyList();
    }

    List<?> actions = (List<?>) actionsObject;
    return actions.stream()
        .map(this::resolveActionButtonRef)
        .filter(Objects::nonNull)
        .map(ref -> Collections.singletonMap("$ref", ref))
        .collect(Collectors.toList());
  }

  private String resolveActionButtonRef(Object actionEntry) {
    if (actionEntry == null) {
      return null;
    }

    if (actionEntry instanceof Map) {
      Object ref = ((Map<?, ?>) actionEntry).get("$ref");
      return ref == null ? null : ref.toString();
    }

    String actionName = actionEntry.toString();
    return ACTION_BUTTON_REFS.getOrDefault(actionName, actionName + "Button");
  }

  private boolean isAdvancedAction(String action) {
    return ADVANCED_ACTIONS.contains(action);
  }

  private Map<String, Object> resolvePayload(SubmitTaskVO submitTask, String action) {
    Map<String, Object> payload = submitTask.getPayload();
    if (payload != null) {
      return payload;
    }

    if (ACTION_TRANSFER.equals(action) && submitTask.getTransfer() != null) {
      return Collections.<String, Object>singletonMap("targetUserId", submitTask.getTransfer().getTargetUserId());
    }

    if (ACTION_ADD_SIGN.equals(action) && submitTask.getAddSign() != null) {
      Map<String, Object> addSignPayload = new HashMap<>();
      addSignPayload.put("targetUserIds", submitTask.getAddSign().getTargetUserIds());
      if (submitTask.getAddSign().getPosition() != null) {
        addSignPayload.put("position", submitTask.getAddSign().getPosition().name());
      }
      return addSignPayload;
    }

    if (ACTION_FREE_REJECT.equals(action) && submitTask.getFreeReject() != null) {
      return Collections.<String, Object>singletonMap("targetNodeId", submitTask.getFreeReject().getTargetNodeId());
    }

    if (ACTION_FREE_RECOVER.equals(action) && submitTask.getRecovery() != null) {
      return Collections.<String, Object>singletonMap("targetNodeId", submitTask.getRecovery().getTargetNodeId());
    }

    if ((ACTION_FREE_APPROVE.equals(action) || ACTION_FREE_APPROVAL.equals(action))
        && submitTask.getFreeApproval() != null) {
      return Collections.<String, Object>singletonMap("consultUserIds", submitTask.getFreeApproval().getConsultUserIds());
    }

    return null;
  }
}
