package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uischema.schema.*;
import com.ouroboros.uischema.schema.event.EventActionConfig;
import com.ouroboros.util.Converter;

class CrudUiSchemaActionProcessor {
  private final DefaultBusinessModel businessModel;
  private final Map<String, Object> config;

  CrudUiSchemaActionProcessor(DefaultBusinessModel businessModel, Map<String, Object> config) {
    this.businessModel = businessModel;
    this.config = config;
  }

  void preprocessHeaderToolbar() {
    if (!showHeaderToolbarActions()) {
      return;
    }
    var actions = new ArrayList<ActionNode>();

    // 新增操作
    var addAction = buildAddAction();
    // 自定义操作
    var customActions = buildCustomHeaderActions();

    if (addAction != null) {
      actions.add(addAction);
    }
    actions.addAll(customActions);

    config.put("headerToolbarActions", actions.stream()
        .sorted(Comparator.comparing(node -> node.getProp(Number.class, "order", 0).doubleValue()))
        .collect(Collectors.toList()));
  }

  void preprocessOperations() {
    if (!showOperations()) {
      return;
    }
    var operations = new ArrayList<ActionNode>();

    // 编辑操作
    var editAction = buildEditAction();
    // 详情操作
    var detailAction = buildDetailAction();
    // 删除操作
    var deleteAction = buildDeleteAction();
    var startProcessAction = buildStartProcessAction();
    var processAction = buildProcessAction();
    var processDetailAction = buildProcessDetailAction();
    // 自定义操作列表
    var customOperations = buildCustomOperations();

    if (editAction != null) {
      operations.add(editAction);
    }
    if (detailAction != null) {
      operations.add(detailAction);
    }
    operations.addAll(customOperations);
    if (deleteAction != null) {
      operations.add(deleteAction);
    }
    if (startProcessAction != null) {
      operations.add(startProcessAction);
    }
    if (processAction != null) {
      operations.add(processAction);
    }
    if (processDetailAction != null) {
      operations.add(processDetailAction);
    }

    // 操作栏属性
    var ocf = getOperationColumnConfig();
    var fixed = ocf.isFixed();
    var width = ocf.getWidth();

    config.put("operationsColumnWidth", width);
    config.put("operationsColumnFixed", fixed);
    config.put("operations", operations.stream()
        .sorted(Comparator.comparing(node -> node.getProp(Number.class, "order", 0).doubleValue()))
        .collect(Collectors.toList()));
  }

  void preprocessActions() {
    var bulkActions = config.get("bulkActions");
    var itemActions = config.get("itemActions");
    if (bulkActions instanceof List<?> bulkActionList && !bulkActionList.isEmpty()) {
      return;
    }
    if (!(itemActions instanceof List<?> itemActionList) || itemActionList.isEmpty()) {
      return;
    }
    var hidden = new SchemaNode();
    hidden.setType("hidden");
    config.put("bulkActions", Collections.singletonList(hidden));
  }

  private Boolean showOperations() {
    return Optional
        .ofNullable(config.getOrDefault("showOperations", true))
        .map(Converter::toBoolean)
        .orElse(true);
  }

  private Boolean showHeaderToolbarActions() {
    return Optional.ofNullable(config.getOrDefault("showHeaderToolbarActions", true))
        .map(Converter::toBoolean)
        .orElse(true);
  }

  private Boolean isAddEnabled() {
    return Optional.ofNullable(config.get("isAddEnabled"))
        .map(Converter::toBoolean)
        .orElse(businessModel.getExtraPropDeep(Boolean.class, "views", "add", "isEnabled").orElse(false));
  }

  private Boolean isEditEnabled() {
    return Optional.ofNullable(config.get("isEditEnabled"))
        .map(Converter::toBoolean)
        .orElse(businessModel.getExtraPropDeep(Boolean.class, "views", "edit", "isEnabled").orElse(false));
  }

  private Boolean isDeleteEnabled() {
    return Optional.ofNullable(config.get("isDeleteEnabled"))
        .map(Converter::toBoolean)
        .orElse(businessModel.getExtraPropDeep(Boolean.class, "apis", "delete", "isEnabled").orElse(false));
  }

  private Boolean isDetailEnabled() {
    return Optional.ofNullable(config.get("isDetailEnabled"))
        .map(Converter::toBoolean)
        .orElse(businessModel.getExtraPropDeep(Boolean.class, "views", "detail", "isEnabled").orElse(false));
  }


  private Boolean isWorkflowEnabled() {
    return Optional.ofNullable(config.get("workflowEnabled"))
        .map(Converter::toBoolean)
        .orElse(false);
  }

  private Boolean isShowProcessButton() {
    return Optional.ofNullable(config.get("showProcessButton"))
        .map(Converter::toBoolean)
        .orElse(false);
  }

  private OperationsColumnConfig getOperationColumnConfig() {
    return new OperationsColumnConfig(config.get("operationsColumn"));
  }

  private String getStatusField() {
    return Optional.ofNullable(config.get("statusField"))
        .map(String::valueOf)
        .orElse("status");
  }

  private String getDraftStatus() {
    var draftStatus = getStatusMap().get("draft");
    var draftStatusCode = Optional.ofNullable(draftStatus)
        .map(m -> m.get("code"))
        .map(String::valueOf)
        .orElse("__DRAFT__NOT__DEFINED");
    return draftStatusCode;
  }

  private Map<String, Map<String, Object>> getStatusMap() {
    return Optional.ofNullable(config.get("statusMap"))
        .filter(Map.class::isInstance)
        .map(m -> (Map<String, Map<String, Object>>) m)
        .orElseGet(Collections::emptyMap);
  }

  private List<ActionNode> buildCustomHeaderActions() {
    List<ActionNode> customHeaderToolbar = Optional.ofNullable(config.get("customToolbar"))
        .filter(List.class::isInstance)
        .map(l -> (List<Object>) l)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(m -> buildCustomActionNode(m, Collections.emptyMap()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return customHeaderToolbar;
  }

  private List<ActionNode> buildCustomOperations() {
    var customOperations = Optional.ofNullable(config.get("customOperations"))
        .filter(List.class::isInstance)
        .map(l -> (List<Object>) l)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(m -> buildCustomActionNode(m, Collections.singletonMap("id", "${id}")))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return customOperations;
  }

  private ActionNode buildCustomActionNode(Object rawConfig, Map<String, String> params) {
    if (rawConfig instanceof ActionNode actionNode) {
      return actionNode;
    }
    if (rawConfig instanceof SchemaNode schemaNode) {
      return new ActionNode(schemaNode);
    }
    if (!(rawConfig instanceof Map map)) {
      return null;
    }

    var configMap = (Map<String, String>) map;
    var mode = configMap.get("mode");
    var url = configMap.get("url");
    var label = configMap.get("label");
    var icon = configMap.get("icon");
    var actionType = switch (mode) {
      case "tab" -> "openTab";
      case "dialog" -> "dialog";
      case "drawer" -> "drawer";
      default -> "dialog";
    };
    var action = new ActionNode();
    var eventAction = new EventActionConfig();
    action.setLabel(label);
    action.setIcon(icon);
    action.addOnEvent("click", eventAction);

    eventAction.setActionType(actionType);

    if ("tab".equalsIgnoreCase(mode)) {
      eventAction.putArg("url", url);
      if (!params.isEmpty()) {
        eventAction.putArg("params", params);
      }
      return action;
    }
    // dialog or drawer
    else {
      var modalSchema = new CommonContainer();
      var amisSchema = new SchemaNode();
      amisSchema.setType("amis-schema");
      amisSchema.setProp("schemaId", url);
      modalSchema.setType(actionType);
      modalSchema.setBody(amisSchema);
      modalSchema.setProp("data", params);
      eventAction.setProp(actionType, modalSchema);
    }

    return action;
  }


  private ActionNode buildAddAction() {
    if (!isAddEnabled()) {
      return null;
    }

    var addOpenMode = businessModel.getExtraPropDeep(String.class, "views", "add", "mode").orElse("dialog");
    var baseUrl = BusinessModelUtil.buildKebabCasePath(businessModel);
    var addViewUrl = baseUrl + "/add";
    var button = new ActionNode();

    button.setProp("order", 10);
    button.setLabel("新增");
    button.setIcon("fa fa-plus");
    button.setProp("align", "right");
    button.setLevel("primary");
    button.setProp("hasAuth", businessModel.getFullName() + ".add");

    if ("tab".equals(addOpenMode)) {
      var openOrSwitchTabAction = new EventActionConfig();
      openOrSwitchTabAction.setActionType("openOrSwitchTab");
      openOrSwitchTabAction.setArgs(new HashMap<>() {{
        put("url", addViewUrl);
        put("reloadOnReceive", true);
      }});
      button.addOnEvent("click", openOrSwitchTabAction);
      return button;
    }

    button.setActionType(addOpenMode);
    var container = new CommonContainer();
    var size = businessModel.getExtraPropDeep(String.class, "views", "add", addOpenMode, "size").orElse("md");
    button.setProp(addOpenMode, container);

    var title = businessModel.getExtraPropDeep(String.class, "views", "add", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse("新增" + businessModel.getTitle());
    container.setProp("title", title);
    container.setType(addOpenMode);
    container.setProp("size", size);
    container.setProp("url", addViewUrl);
    container.setProp("data", Collections.emptyMap());
    container.setProp("rewriteProps", Collections.singletonMap("wrapWithPanel", false));

    if ("drawer".equalsIgnoreCase(addOpenMode)) {
      container.setProp("resizable", businessModel.getExtraPropDeep(Boolean.class, "views", "add", "drawer", "resizable").orElse(null));
    }

    return button;
  }

  private ActionNode buildEditAction() {
    if (!isEditEnabled()) {
      return null;
    }

    var editOpenMode = businessModel.getExtraPropDeep(String.class, "views", "edit", "mode")
        .orElse("dialog");
    var baseUrl = BusinessModelUtil.buildKebabCasePath(businessModel);
    var editViewUrl = baseUrl + "/edit";
    var button = new ActionNode();

    // 只有草稿状态时才显示
    var statusField = getStatusField();
    if (isWorkflowEnabled()) {
      Map<String, Map<String, Object>> statusMap = businessModel.getExtraPropDeep(Map.class, "workflow", "bindings", "0", "statusMap")
          .orElseGet(Collections::emptyMap);
      List<String> restartableStatus = ((List<String>) businessModel.getExtraPropDeep(List.class, "workflow", "restartableStatus")
          .orElseGet(Collections::emptyList))
          .stream()
          .map(s -> statusMap.get(s).get("code"))
          .filter(Objects::nonNull)
          .map(Object::toString)
          .collect(Collectors.toList());

      Set<String> allStatus = new HashSet<>();
      allStatus.add(getDraftStatus());
      allStatus.addAll(restartableStatus);
      String statusStr = allStatus.stream()
          .map(status -> "'" + status + "'")
          .collect(Collectors.joining(","));

      button.setVisibleOn("${ ARRAYINCLUDES([" + statusStr + "], CONCATENATE(GET(" + statusField + ", 'code', " + statusField + "))) || processInfo.tasks[0].nodeType == 'START' }");
    }

    button.setClassName("operation-button edit-operation");
    button.setProp("order", 20);
    var config = getOperationColumnConfig();
    var buttonStyle = config.getButtonStyle();
    if ("default".equalsIgnoreCase(buttonStyle)) {
      button.setIcon("fa fa-pencil");
      button.setLevel("enhance");
      button.setLabel("编辑");
    } else if ("textButton".equalsIgnoreCase(buttonStyle)) {
      button.setLabel("编辑");
      var buttonSize = config.getButtonSize();
      var buttonIcon = config.getButtonIcon();
      if (!"sm".equals(buttonSize)) {
        button.setSize(buttonSize);
      }
      if (buttonIcon) {
        button.setIcon("fa fa-pencil");
      }
      button.setLevel("primary");
    }
    button.setProp("hasAuth", businessModel.getFullName() + ".edit");

    if ("tab".equals(editOpenMode)) {
      var openOrSwitchTabAction = new EventActionConfig();
      openOrSwitchTabAction.setActionType("openOrSwitchTab");
      openOrSwitchTabAction.putArg("url", editViewUrl + "?id=${id}");
      openOrSwitchTabAction.putArg("reloadOnReceive", true);
      button.addOnEvent("click", openOrSwitchTabAction);
      return button;
    }

    var container = new SchemaNode();
    var size = businessModel.getExtraPropDeep(String.class, "views", "edit", editOpenMode, "size").orElse("md");

    button.setActionType(editOpenMode);
    button.setProp(editOpenMode, container);

    var title = businessModel.getExtraPropDeep(String.class, "views", "edit", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse("编辑" + businessModel.getTitle());
    container.setProp("title", title);
    container.setType(editOpenMode);
    container.setProp("size", size);
    container.setProp("url", editViewUrl);
    container.setProp("data", Collections.singletonMap("id", "${ id }"));
    container.setProp("rewriteProps", Collections.singletonMap("wrapWithPanel", false));

    if ("drawer".equalsIgnoreCase(editOpenMode)) {
      container.setProp("resizable", businessModel.getExtraPropDeep(Boolean.class, "views", "edit", "drawer", "resizable").orElse(null));
    }

    return button;
  }

  private ActionNode buildDetailAction() {
    if (!isDetailEnabled()) {
      return null;
    }

    var detailOpenMode = businessModel.getExtraPropDeep(String.class, "views", "detail", "mode")
        .orElse("dialog");
    var baseUrl = BusinessModelUtil.buildKebabCasePath(businessModel);
    var detailViewUrl = baseUrl + "/detail";
    var button = new ActionNode();
    var config = getOperationColumnConfig();
    var buttonStyle = config.getButtonStyle();

    button.setClassName("operation-button detail-operation");
    button.setProp("order", 10);

    // 当关联了流程时隐藏详情按钮
    if (isWorkflowEnabled()) {
      button.setHiddenOn("${processInfo && processInfo.instanceId}");
    }

    if ("default".equalsIgnoreCase(buttonStyle)) {
      button.setLabel("详情");
      button.setIcon("fa fa-eye");
      button.setLevel("enhance");
    } else if ("textButton".equalsIgnoreCase(buttonStyle)) {
      button.setLabel("详情");
      var buttonSize = config.getButtonSize();
      var buttonIcon = config.getButtonIcon();
      var buttonColorSet = config.getButtonColorSet();
      if (!"sm".equals(buttonSize)) {
        button.setSize(buttonSize);
      }
      if (buttonIcon) {
        button.setIcon("fa fa-eye");
      }
      if (buttonColorSet.startsWith("primary")) {
        button.setLevel("primary");
      } else if (buttonColorSet.startsWith("default")) {
        button.setLevel("default");
      }
    }
    if ("tab".equals(detailOpenMode)) {
      var openOrSwitchTabAction = new EventActionConfig();
      openOrSwitchTabAction.setActionType("openOrSwitchTab");
      openOrSwitchTabAction.putArg("url", detailViewUrl + "?id=${id}");
      button.addOnEvent("click", openOrSwitchTabAction);
      return button;
    }

    button.setActionType(detailOpenMode);
    var container = new SchemaNode();
    var size = businessModel.getExtraPropDeep(String.class, "views", "detail", detailOpenMode, "size").orElse("md");


    button.setProp(detailOpenMode, container);

    var title = businessModel.getExtraPropDeep(String.class, "views", "detail", "title")
        .filter(StringUtils::isNotEmpty)
        .orElse("查看" + businessModel.getTitle());
    container.setProp("title", title);
    container.setType(detailOpenMode);
    container.setProp("size", size);
    container.setProp("url", detailViewUrl);
    container.setProp("data", Collections.singletonMap("id", "${ id }"));
    container.setProp("rewriteProps", Collections.singletonMap("wrapWithPanel", false));

    if ("drawer".equalsIgnoreCase(detailOpenMode)) {
      container.setProp("resizable", businessModel.getExtraPropDeep(Boolean.class, "views", "detail", "drawer", "resizable").orElse(null));
    }

    return button;
  }

  private ActionNode buildDeleteAction() {
    if (!isDeleteEnabled()) {
      return null;
    }
    var deleteApiUrl = "/api" + BusinessModelUtil.buildKebabCasePath(businessModel) + "/${id}";
    var button = new ActionNode();
    var api = new ApiConfig();

    api.setMethod("delete");
    api.setUrl(deleteApiUrl);
    api.setResponseData(Collections.emptyMap());

    button.setProp("api", api);
    button.setClassName("operation-button delete-operation");
    button.setActionType("ajax");

    var config = getOperationColumnConfig();
    var buttonStyle = config.getButtonStyle();
    if ("default".equalsIgnoreCase(buttonStyle)) {
      button.setLevel("enhance");
      button.setClassName("operation-button delete-operation text-danger border-danger");
      button.setIcon("fa fa-times text-danger");
      button.setLabel("删除");
    } else if ("textButton".equalsIgnoreCase(buttonStyle)) {
      button.setLabel("删除");
      var buttonSize = config.getButtonSize();
      var buttonIcon = config.getButtonIcon();
      if (!"sm".equals(buttonSize)) {
        button.setSize(buttonSize);
      }
      if (buttonIcon) {
        button.setIcon("fa fa-times");
      }
      button.setLevel("danger");
    }

    button.setConfirmText("确定要删除？");
    button.setProp("order", 30);
    button.setProp("hasAuth", businessModel.getFullName() + ".delete");

    // 只有草稿状态时才可以删除
    var statusField = getStatusField();
    if (isWorkflowEnabled()) {
      button.setVisibleOn("${ CONCATENATE(GET(" + getStatusField() + ", 'code', " + statusField + ")) == '" + getDraftStatus() + "' }");
    }

    return button;
  }

  private ActionNode buildStartProcessAction() {
    if (!isWorkflowEnabled()) {
      return null;
    }

    var statusField = Objects.toString(config.get("statusField"));
    Map<String, Map<String, Object>> statusMap = businessModel.getExtraPropDeep(Map.class, "workflow", "bindings", "0", "statusMap")
        .orElseGet(Collections::emptyMap);
    List<String> restartableStatus = ((List<String>) businessModel.getExtraPropDeep(List.class, "workflow", "restartableStatus")
        .orElseGet(Collections::emptyList))
        .stream()
        .map(s -> statusMap.get(s).get("code"))
        .filter(Objects::nonNull)
        .map(Object::toString)
        .collect(Collectors.toList());

    var submittedStatus = Objects.toString(config.get("submittedStatus"));
    Set<String> allStatus = new HashSet<>();
    allStatus.add(submittedStatus);
    allStatus.addAll(restartableStatus);
    String statusStr = allStatus.stream()
        .map(status -> "'" + status + "'")
        .collect(Collectors.joining(","));

    var button = new ActionNode();
    button.setClassName("operation-button start-process-operation");
    button.setLabel("发起流程");
    button.setIcon("fa fa-redo-alt");
    button.setLevel("enhance");
    button.setProp("order", 15);
    button.setVisibleOn("${ ARRAYINCLUDES([" + statusStr + "], CONCATENATE(GET(" + statusField + ", 'code', " + statusField + "))) && !(processInfo && processInfo.instanceId) }");
    button.setConfirmTitle("确认");
    button.setConfirmText("是否提交" + businessModel.getTitle() + "至工作流审批？");
    button.addOnEvent("click", buildStartProcessEventActions());

    return button;
  }

  private List<EventActionConfig> buildStartProcessEventActions() {
    var startApi = WorkflowStartActionBuilder.buildStartProcessApi(
        "/api" + BusinessModelUtil.buildKebabCasePath(businessModel) + "/${id}/start-process");
    var actions = new ArrayList<EventActionConfig>();

    actions.add(WorkflowStartActionBuilder.buildCurrentRecordRequirementsAction(getWorkflowProcessId()));
    actions.add(WorkflowStartActionBuilder.buildRequirementsFailureToastAction());
    actions.add(WorkflowStartActionBuilder.buildStopWhenRequirementsFailureAction());
    actions.add(WorkflowStartActionBuilder.buildAssigneeDialogAction());
    actions.add(WorkflowStartActionBuilder.buildStopWhenAssigneeSelectionCanceledAction());
    actions.add(WorkflowStartActionBuilder.buildStartProcessAction(startApi));
    actions.add(buildStartProcessFailureToastAction());
    actions.add(buildStopWhenStartProcessFailureAction());
    actions.add(buildStartProcessSuccessToastAction());
    actions.add(buildReloadListAction());
    return actions;
  }

  private EventActionConfig buildStartProcessFailureToastAction() {
    var action = new EventActionConfig();
    var args = new LinkedHashMap<String, Object>();
    args.put("msgType", "error");
    args.put("msg", "发起审批失败");
    action.setActionType("toast");
    action.setExpression("${startProcessResult.responseStatus !== 0}");
    action.setProp("args", args);
    return action;
  }

  private EventActionConfig buildStopWhenStartProcessFailureAction() {
    var action = new EventActionConfig();
    action.setActionType("stopPropagation");
    action.setExpression("${startProcessResult.responseStatus !== 0}");
    return action;
  }

  private EventActionConfig buildStartProcessSuccessToastAction() {
    var action = new EventActionConfig();
    var args = new LinkedHashMap<String, Object>();
    args.put("msgType", "success");
    args.put("msg", "成功发起审批");
    action.setActionType("toast");
    action.setProp("args", args);
    return action;
  }

  private EventActionConfig buildReloadListAction() {
    var action = new EventActionConfig();
    action.setActionType("reload");
    action.setComponentId("default-crud-list");
    return action;
  }

  private String getWorkflowProcessId() {
    return businessModel.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse("");
  }

  private ActionNode buildProcessAction() {
    if (!isWorkflowEnabled()) {
      return null;
    }
    if (!isShowProcessButton()) {
      return null;
    }

    var button = new ActionNode();
    var dialog = new CommonContainer();
    // 取编辑弹窗大小
    var editOpenMode = businessModel.getExtraPropDeep(String.class, "views", "edit", "mode").orElse("dialog");
    var size = businessModel.getExtraPropDeep(String.class, "views", "edit", editOpenMode, "size").orElse("full");

    button.setClassName("operation-button process-operation");
    button.setLabel("处理");
    button.setIcon("fa fa-redo-alt");
    button.setLevel("enhance");
    button.setProp("order", 15);
    button.setVisibleOn("${processInfo && processInfo.instanceId && processInfo.processable && processInfo.processableTaskId}");
    button.setActionType("dialog");
    button.setProp("dialog", dialog);

    dialog.setProp("size", size);
    dialog.setType("dialog");
    dialog.setProp("title", "处理");
    dialog.setProp("url", BusinessModelUtil.buildKebabCasePath(businessModel) + "/${id}/approval?instanceId=${processInfo.instanceId}&taskId=${processInfo.processableTaskId}");

    return button;
  }

  private ActionNode buildProcessDetailAction() {
    if (!isWorkflowEnabled()) {
      return null;
    }

    var button = new ActionNode();
    var dialog = new CommonContainer();
    // 取详情弹窗大小
    var detailOpenMode = businessModel.getExtraPropDeep(String.class, "views", "detail", "mode").orElse("dialog");
    var size = businessModel.getExtraPropDeep(String.class, "views", "detail", detailOpenMode, "size").orElse("full");

    button.setClassName("operation-button process-detail-operation");
    button.setLabel("详情");
    button.setIcon("fa fa-eye");
    button.setLevel("enhance");
    button.setProp("order", 10);
    button.setActionType("dialog");
    button.setProp("dialog", dialog);
    button.setVisibleOn("${processInfo && processInfo.instanceId}");

    dialog.setProp("size", size);
    dialog.setType("dialog");
    dialog.setProp("title", "流程详情");
    dialog.setProp("url", BusinessModelUtil.buildKebabCasePath(businessModel) + "/${id}/process-detail?instanceId=${processInfo.instanceId}");

    return button;
  }

  static class OperationsColumnConfig {
    Map<String, Object> configMap;

    public OperationsColumnConfig(Object config) {
      if (config instanceof Map<?, ?> map) {
        configMap = (Map) map;
      } else if (config instanceof OperationsColumnConfig cfg) {
        configMap = cfg.configMap;
      } else {
        configMap = Collections.emptyMap();
      }
    }

    public String getButtonStyle() {
      return Optional.ofNullable(configMap.get("buttonStyle"))
          .map(Object::toString)
          .filter(StringUtils::isNotBlank)
          .orElse("default");
    }

    public Boolean getButtonIcon() {
      return Optional.ofNullable(configMap.get("buttonIcon"))
          .map(Converter::toBoolean)
          .orElse(false);
    }

    public String getButtonSize() {
      return Optional.ofNullable(configMap.get("buttonSize"))
          .map(Object::toString)
          .filter(StringUtils::isNotBlank)
          .orElse("md");
    }

    public String getButtonColorSet() {
      return Optional.ofNullable(configMap.get("buttonColorSet"))
          .map(Object::toString)
          .filter(StringUtils::isNotBlank)
          .orElse("primary-primary-danger");
    }

    public Object getWidth() {
      var width = configMap.get("width");
      if (ObjectUtils.isEmpty(width)) {
        return null;
      }
      return width;
    }

    public Boolean isFixed() {
      return Optional.ofNullable(configMap.get("fixed"))
          .map(Converter::toBoolean)
          .orElse(true);
    }
  }
}
