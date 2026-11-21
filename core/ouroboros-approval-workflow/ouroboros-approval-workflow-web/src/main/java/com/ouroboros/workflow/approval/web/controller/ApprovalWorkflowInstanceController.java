package com.ouroboros.workflow.approval.web.controller;

import com.ouroboros.workflow.approval.FlowInstanceVariables;
import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowStartResult;

import static com.ouroboros.workflow.approval.model.TaskState.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Dates;
import com.ouroboros.util.Maps;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.candidates.StarterCandidate;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.web.service.ApprovalWorkflowDiagramResultAssembler;
import com.ouroboros.workflow.approval.web.service.ApprovalWorkflowStartRequirementsService;
import com.ouroboros.workflow.approval.web.user.CandidateUserResolverRegistry;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowDiagramResult;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowStartRequirementsResult;
import com.ouroboros.workflow.approval.web.vo.CardItemResult;
import com.ouroboros.workflow.approval.web.vo.ModelCardResult;
import com.ouroboros.workflow.approval.web.vo.StartFlowInstanceData;


@RestController
@RequestMapping("/api/approval-workflow/instance")
public class ApprovalWorkflowInstanceController {
  static Logger logger = LoggerFactory.getLogger(ApprovalWorkflowInstanceController.class);

  /**
   * 可发起流程的取数上限。该接口的产品语义是"一次给全、前端按分类渲染"，
   * 不是分页列表；上限只用于防止定义数量异常时把整表读进内存。
   */
  private static final int ALL_AVAILABLE = 1000;
  final ApprovalWorkflowDiagramResultAssembler approvalWorkflowDiagramResultAssembler =
      new ApprovalWorkflowDiagramResultAssembler();
  final ApprovalWorkflowStartRequirementsService startRequirementsService =
      new ApprovalWorkflowStartRequirementsService();

  @Autowired
  FlowInstanceService flowInstanceService;

  @Autowired
  WorkflowModelService workflowModelService;

  @Autowired
  FlowDefinitionService flowDefinitionService;

  @Autowired
  CandidateUserResolverRegistry candidateUserResolverRegistry;

  @Autowired
  UsernameResolverRegistry usernameResolverRegistry;

  /** 引擎无关发起需求解析器（Phase 4 M22：内置 Default + EXT 路由 @Primary）。 */
  @Autowired
  StartRequirementsResolver startRequirementsResolver;

  /**
   * 可发起流程列表：按分类分组，**一次返回全部分组**。
   *
   * <p>契约（由前端 amis schema `approval-center/start.ui-schema.json` 决定）：它以
   * {@code each → items → cards} 消费本接口，请求 {@code page=1&perPage=1000}，
   * 自身不做前端分页——即"服务型接口 + 前端按分类渲染"。
   *
   * <p>因此这里以前存在的两个缺陷都不再成立：
   * <ul>
   *   <li>分页发生在按分类聚合**之前**，而 {@code searchAvailableByPage} 不按分类排序，
   *       同一分类可能被拆到相邻两页（表现为卡片重复或翻页丢失）——现在不再分页；</li>
   *   <li>{@code total} 曾用"本页聚合出的分类数"充当——现在明确为**可发起流程总数**，
   *       与 {@code items}（分类分组）口径不同，调用方不得用它推断分组数。</li>
   * </ul>
   *
   * <p>{@code page}/{@code perPage} 保留为兼容入参（前端仍在传），但不参与截断：
   * 传任意值都返回全部分组。真正需要分页时应改为"后端按分类聚合后再分页"并同步前端契约。
   */
  @Operation(
      summary = "获取可发起流程列表",
      description = "按分类分组返回当前用户可发起的流程卡片；一次返回全部分组，total 为可发起流程总数。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回可发起流程列表")
  })
  @GetMapping("/available")
  public PagedWebApiResult<ModelCardResult> getAvailable(
      @Parameter(description = "页码（保留入参，不参与截断）") @RequestParam(required = false) Integer page,
      @Parameter(description = "每页条数（保留入参，不参与截断）") @RequestParam(required = false) Integer perPage) {
    PagedWebApiResult<WorkflowModel> models = workflowModelService.searchAvailableByPage(null, null, 1, ALL_AVAILABLE);
    Map<String, ModelCardResult> cardsByCategory = new LinkedHashMap<>();
    for (WorkflowModel model : models.getData().getItems()) {
      ModelCardResult card = cardsByCategory.computeIfAbsent(
          model.getCategory(), category -> new ModelCardResult(category, new ArrayList<>()));
      card.getCards().add(new CardItemResult(model.getId(), model.getCategory(), model.getName(),
          model.getDescription(), model.getIcon(), model.getProcessDefinitionId()));
    }
    List<ModelCardResult> res = new ArrayList<>(cardsByCategory.values());
    return PagedWebApiResult.ok(models.getData().getTotal(), res);
  }

  @Operation(
      summary = "获取流程发起表单",
      description = "根据流程标识获取发起流程时的初始化表单和候选人时间线。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程发起表单")
  })
  @GetMapping("/{flowKey}/init-form")
  public WebApiResult<?> getStartForm(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String flowKey) {
    String currentUserId = getCurrentUserId();
    var formResult = flowInstanceService.getInitialForm(flowKey);
    if (formResult.isFailure()
        && formResult.getCause() instanceof com.ouroboros.workflow.approval.FlowFormServiceUnavailableException unavailable) {
      SchemaNode guidance = new SchemaNode();
      guidance.setType("alert");
      guidance.setProp("level", "info");
      guidance.setProp("body", unavailable.getMessage());
      guidance.setProp("formAvailable", false);
      return WebApiResult.ok(guidance);
    }
    SchemaNode formNode = formResult
        .getOrElseThrow((e) -> new BusinessException("获取初始化表单失败: " + e.getMessage(), e));
    Object formBody = formNode.getProp("body");
    if (formBody instanceof List<?>) {
      ((List<?>) formBody).stream()
          .filter(SchemaNode.class::isInstance)
          .map(SchemaNode.class::cast)
          .forEach(body -> {
            body.setProp("name", "formData." + body.getProp("name"));
            body.setProp("value", null);
          });
    }

    List<FlowNode> flowNodes = flowDefinitionService.getFlowNodes(flowKey);
    List<SchemaNode> timelineItems = flowNodes.stream()
        .map(node -> {
          String nodeId = node.getId();

          String color;
          SchemaNode titleNode = new SchemaNode();

          List<Candidate> candidates = node.getCandidates();
          if (candidates != null && candidates.stream().anyMatch(StarterCandidate.class::isInstance)) {
            titleNode.setType("tpl");
            titleNode.setProp("tpl", "流程发起人");

            color = "success";
          } else if (candidates != null && candidates.stream().anyMatch(UserSelectCandidate.class::isInstance)) {
            List<SchemaNode> body = new ArrayList<>();

            {
              SchemaNode textNode = new SchemaNode();
              textNode.setType("tpl");
              textNode.setProp("tpl", "${taskAssignee." + nodeId + "|pick:name}");
              body.add(textNode);
            }

            {
              SchemaNode dialogFormBody = startRequirementsService.buildTaskAssigneePicker(node, false);

              FormNode dialogBody = new FormNode();
              dialogBody.setProp("body", dialogFormBody);

              List<SchemaNode> dialogActions = new ArrayList<>();
              {
                SchemaNode action = new SchemaNode();
                action.setType("action");
                action.setProp("actionType", "cancel");
                action.setProp("label", "取消");
                dialogActions.add(action);
              }
              {
                SchemaNode action = new SchemaNode();
                action.setType("action");
                action.setProp("actionType", "confirm");
                action.setProp("level", "primary");
                action.setProp("label", "确认");
                action.setProp("mergeData", true);
                dialogActions.add(action);
              }

              SchemaNode dialogNode = new SchemaNode();
              dialogNode.setProp("title", "请选择");
              dialogNode.setProp("size", "lg");
              dialogNode.setProp("body", dialogBody);
              dialogNode.setProp("actions", dialogActions);

              SchemaNode buttonNode = new SchemaNode();
              buttonNode.setType("button");
              buttonNode.setProp("label", "选择");
              buttonNode.setProp("size", "xs");
              buttonNode.setProp("className", "ml-2");
              buttonNode.setProp("actionType", "dialog");
              buttonNode.setProp("level", "default");
              buttonNode.setProp("dialog", dialogNode);

              body.add(buttonNode);
            }

            titleNode.setType("wrapper");
            titleNode.setProp("className", "m-0 p-0");
            titleNode.setProp("body", body);

            color = "warning";
          } else {
            String users = "无";
            if (candidates != null) {
              List<String> userIds = candidateUserResolverRegistry.resolve(candidates).stream()
                  .map(user -> user.replace("${starter}", currentUserId))
                  .collect(Collectors.toList());

              users = String.join(", ", usernameResolverRegistry.resolve(userIds).values());

            }

            titleNode.setType("tpl");
            titleNode.setProp("tpl", users);

            color = "success";
          }

          SchemaNode schemaNode = new SchemaNode();
          schemaNode.setProp("time", node.getName());
          schemaNode.setProp("title", titleNode);
          schemaNode.setProp("color", color);

          return schemaNode;
        })
        .collect(Collectors.toList());

    SchemaNode timeline = new SchemaNode();
    timeline.setType("timeline");
    timeline.setProp("items", timelineItems);

    List<SchemaNode> body = new ArrayList<>();
    body.add(formNode);
    body.add(timeline);

    SchemaNode node = new SchemaNode();
    node.setType("container");
    node.setProp("formAvailable", true);
    node.setProp("body", body);

    return WebApiResult.ok(node);
  }

  @Operation(
      summary = "查询流程发起前置要求",
      description = "根据当前流程定义和发起表单数据，返回发起前需要补充的输入项。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程发起前置要求")
  })
  @PostMapping("/{flowKey}/start-requirements")
  public WebApiResult<ApprovalWorkflowStartRequirementsResult> getStartRequirements(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String flowKey,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "流程发起上下文")
      @RequestBody(required = false) StartFlowInstanceData data) {
    Map<String, Object> formData = data == null || data.getFormData() == null
        ? Collections.emptyMap()
        : data.getFormData();
    StartRequirements startRequirements = startRequirementsResolver
        .getStartRequirements(flowKey, formData)
        .getOrElseThrow(ex -> new BusinessException(
            "[" + ApprovalWorkflowErrorCodes.WORKFLOW_START_REQUIREMENTS_RESOLVE_FAILED + "] 发起需求解析失败: "
                + ex.getMessage(),
            ex));
    SchemaNode schema = startRequirementsService.buildStartRequirementsForm(startRequirements);
    return WebApiResult.ok(ApprovalWorkflowStartRequirementsResult.of(startRequirements, schema));
  }

  @Operation(
      summary = "发起流程实例",
      description = "提交发起表单并启动新的流程实例。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程实例发起成功"),
      @ApiResponse(responseCode = "400", description = "流程发起参数不合法")
  })
  @PostMapping("/{flowKey}/start")
  public WebApiResult<String> start(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String flowKey,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "流程发起数据", required = true)
      @RequestBody StartFlowInstanceData data) {
    //因为开启流程的过程就是填写起始表单的过程
    //我们将两个行为合并为一个
    String userId = getCurrentUserId();

    Map<String, List<String>> taskAssignee = new HashMap<>();
    Map<String, List<StartFlowInstanceData.TaskAssignee>> taskAssigneeMap = data.getTaskAssignee();
    if (taskAssigneeMap != null) {
      taskAssigneeMap.keySet().stream()
          .filter(key -> taskAssigneeMap.get(key) != null)
          .forEach(key -> taskAssignee.put(
              key,
              taskAssigneeMap.get(key).stream()
                  .map(StartFlowInstanceData.TaskAssignee::getValue)
                  .collect(Collectors.toList())
          ));
    }
    Map<String, Object> formData = data.getFormData();
    Map<String, Object> variables = new HashMap<>();
    if (data.getBusinessRecordType() != null) {
      variables.put(FlowInstanceVariables.BUSINESS_RECORD_TYPE, data.getBusinessRecordType());
    }
    if (data.getBusinessRecordId() != null) {
      variables.put(FlowInstanceVariables.BUSINESS_RECORD_ID, data.getBusinessRecordId());
    }
    var result = flowInstanceService.start(new FlowStartCommand(
        flowKey, userId, taskAssignee, variables, formData, data.getCc(), data.getIdempotencyKey()));
    if (result.isSuccess()) {
      return new ApprovalWorkflowStartResult(result.get());
    }
    return WebApiResult.error(result.getCause().getMessage());
  }

  @Operation(
      summary = "取消流程实例",
      description = "取消指定流程实例。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程实例取消成功")
  })
  @PostMapping("/{id}/cancel")
  public WebApiResult<Void> cancelInstance(
      @Parameter(description = "流程实例 ID", required = true) @PathVariable String id) {
    return WebApiResult.of(flowInstanceService.cancel(id, getCurrentUserId()));
  }

  @Operation(
      summary = "发起人撤回流程实例",
      description = "仅发起人可撤回、仅审批中实例可撤回；撤回后实例进入终止态，事件类型 CANCELLED、outcome=withdrawn。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程实例撤回成功")
  })
  @PostMapping("/{id}/withdraw")
  public WebApiResult<Void> withdrawInstance(
      @Parameter(description = "流程实例 ID", required = true) @PathVariable String id,
      @RequestBody(required = false) Map<String, Object> body) {
    Object comment = body == null ? null : body.get("comment");
    return WebApiResult.of(flowInstanceService.withdraw(
        id, getCurrentUserId(), comment == null ? "" : String.valueOf(comment)));
  }

  @Operation(
      summary = "统计流程实例",
      description = "按关键字和状态分页统计流程实例数据。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程实例统计结果")
  })
  @GetMapping("/statistic")
  public PagedWebApiResult<FlowInstanceStatistic> statistic(
      @Parameter(description = "流程关键字") @RequestParam(required = false) String keyword,
      @Parameter(description = "流程状态") @RequestParam(required = false) String status,
      @Parameter(description = "页码", required = true) @RequestParam int page,
      @Parameter(description = "每页条数", required = true) @RequestParam int perPage) {
    Long total = flowInstanceService.getStatisticCount(keyword, status);
    if (total == 0) {
      return PagedWebApiResult.ok(total, Collections.emptyList());
    }
    List<FlowInstanceStatistic> items = flowInstanceService.getStatistic(keyword, status, (page - 1) * perPage, perPage);
    return PagedWebApiResult.ok(total, items);
  }

  @Operation(
      summary = "查询流程实例列表",
      description = "按流程定义 ID 和状态分页查询流程实例列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程实例列表")
  })
  @GetMapping("/{flowKey}/list")
  public PagedWebApiResult<?> instanceDetail(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String flowKey,
      @Parameter(description = "流程状态") @RequestParam(required = false) String status,
      @Parameter(description = "页码", required = true) @RequestParam int page,
      @Parameter(description = "每页条数", required = true) @RequestParam int perPage) {
    Long total = flowInstanceService.getInstancesCount(flowKey, status);
    if (total == 0) {
      return PagedWebApiResult.ok(total, Collections.emptyList());
    }
    List<FlowInstanceDetail> items = flowInstanceService.getInstances(flowKey, status, (page - 1) * perPage, perPage);

    // 设置发起人名称
    List<String> users = items.stream().map(FlowInstanceDetail::getStarter).collect(Collectors.toList());
    Map<String, String> userNameMap = usernameResolverRegistry.resolve(users);
    List<Map<String, Object>> result = items.stream()
        .map(Maps::fromBean)
        .peek(item -> {
          String starterName = Optional.ofNullable(item.get("starter"))
              .filter(Objects::nonNull)
              .map(Object::toString)
              .map(userNameMap::get)
              .orElseGet(() -> item.getOrDefault("starter", "未知用户").toString());
          item.put("starterName", starterName);
        })
        .collect(Collectors.toList());

    return PagedWebApiResult.ok(total, result);
  }

  @Operation(summary = "获取流程执行信息", description = "返回执行状态及当前执行可用的页面能力。", tags = {"approval-workflow"})
  @GetMapping("/{instanceId}/info")
  public WebApiResult<com.ouroboros.workflow.approval.model.FlowProcessInfo> instanceInfo(
      @PathVariable String instanceId) {
    return WebApiResult.of(flowInstanceService.getProcessInfo(instanceId));
  }

  @Operation(
      summary = "获取流程实例时间线",
      description = "返回指定流程实例的审批时间线。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程实例时间线")
  })
  @GetMapping("/{instanceId}/timeline")
  public WebApiResult<?> instanceTimeline(
      @Parameter(description = "流程实例 ID", required = true) @PathVariable String instanceId) {
    List<FlowTimeline> nodes = flowInstanceService.getTimeLine(instanceId);

    // 获取审核人名称
    List<String> users = nodes.stream()
        .filter(item -> ObjectUtils.isNotEmpty(item.getTaskInstances()))
        .flatMap(item -> item.getTaskInstances().stream())
        .flatMap(item -> Stream.concat(
            item.getCandidateUsers().stream(),
            Stream.of(item.getAssignee())
        ))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    Map<String, String> userNameMap = usernameResolverRegistry.resolve(users);

    List<SchemaNode> schemaItems = nodes.stream()
        .flatMap(node -> Optional.ofNullable(node.getTaskInstances())
            .orElseGet(Collections::emptyList)
            .stream()
            .map(task -> buildTimelineItem(node, task, userNameMap)))
        .collect(Collectors.toList());

    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("timeline");
    schemaNode.setProp("className", "workflow-approval-timeline");
    schemaNode.setProp("items", schemaItems);

    return WebApiResult.ok(schemaNode);
  }

  private SchemaNode buildTimelineItem(
      FlowTimeline node,
      FlowTimeline.TaskInstance task,
      Map<String, String> userNameMap) {
    String time = task.getEndTime() != null
        ? Dates.toString(task.getEndTime(), "yyyy-MM-dd HH:mm:ss")
        : task.getStartTime() != null
        ? Dates.toString(task.getStartTime(), "yyyy-MM-dd HH:mm:ss")
        : "未开始";
    String icon = task.getState() == APPROVED || task.getState() == HANDLED ? "success"
        : task.getState() == REJECTED ? "fail"
        : "";
    String color = task.getState() == PROCESSING ? "warning"
        : task.getState() == CANCELLED ? "danger"
        : task.getState() == SUSPENDED ? "danger"
        : task.getState() == RESUMED ? "warning"
        : "";
    String participantNames = resolveTimelineParticipantNames(task, userNameMap);

    List<SchemaNode> titleBodySchemas = new ArrayList<>();
    titleBodySchemas.add(buildTimelineText(
        "人员：" + StringUtils.defaultIfBlank(participantNames, "未分配"),
        "workflow-approval-timeline-person text-sm font-medium"));

    String statusLabel = task.getState() == HANDLED ? "已办理" : "";
    if (StringUtils.isNotEmpty(statusLabel)) {
      titleBodySchemas.add(buildTimelineText(statusLabel, "workflow-approval-timeline-meta text-gray-400 text-xs"));
    }

    if (StringUtils.isNotEmpty(task.getComment())) {
      titleBodySchemas.add(buildTimelineText("意见：" + task.getComment(), "workflow-approval-timeline-meta text-gray-500 text-xs"));
    }

    SchemaNode titleSchema = new SchemaNode();
    titleSchema.setType("wrapper");
    titleSchema.setProp("className", "m-0 p-0");
    titleSchema.setProp("body", titleBodySchemas);

    SchemaNode nodeSchema = new SchemaNode();
    nodeSchema.setProp("time", time);
    nodeSchema.setProp("timeClassName", "workflow-approval-timeline-time text-gray-400 text-xs");
    nodeSchema.setProp("titleClassName", "workflow-approval-timeline-title text-sm");
    nodeSchema.setProp("icon", icon);
    nodeSchema.setProp("color", color);
    nodeSchema.setProp("title", titleSchema);
    return nodeSchema;
  }

  private SchemaNode buildTimelineText(String value, String className) {
    SchemaNode textNode = new SchemaNode();
    textNode.setType("text");
    textNode.setProp("className", className);
    textNode.setProp("value", value);
    return textNode;
  }

  @Operation(
      summary = "获取流程实例流程图数据",
      description = "返回流程实例对应定义及当前活动节点 ID 列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程实例流程图数据")
  })
  @GetMapping("/{instanceId}/diagram")
  public WebApiResult<ApprovalWorkflowDiagramResult> instanceDiagram(
      @Parameter(description = "流程实例 ID", required = true) @PathVariable String instanceId) {
    return WebApiResult.of(flowInstanceService.getInstanceFlowDefinitionV2(instanceId)
        .map(definition -> approvalWorkflowDiagramResultAssembler.assemble(
            definition,
            flowInstanceService.getActiveActivityIds(instanceId))));
  }

  private String resolveTimelineParticipantNames(
      FlowTimeline.TaskInstance task,
      Map<String, String> userNameMap) {
    List<String> userIds = ObjectUtils.isNotEmpty(task.getAssignee())
        ? Collections.singletonList(task.getAssignee())
        : Optional.ofNullable(task.getCandidateUsers()).orElseGet(Collections::emptyList);
    return userIds.stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .map(userId -> Optional.ofNullable(userNameMap.get(userId)).orElse(userId))
        .collect(Collectors.joining(", "));
  }

  private String getCurrentUserId() {
    return CurrentUserSupplier.getCurrentUserId()
        .orElse("anonymousUser");
  }
}
