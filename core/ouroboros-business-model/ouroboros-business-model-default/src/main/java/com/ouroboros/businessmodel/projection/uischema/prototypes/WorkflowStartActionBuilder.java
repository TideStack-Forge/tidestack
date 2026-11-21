package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.CommonContainer;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.schema.event.EventActionConfig;

final class WorkflowStartActionBuilder {
  static final String REQUIREMENTS_RESULT = "startRequirementsResult";
  static final String ASSIGNEE_SELECTION = "startAssigneeSelection";
  static final String START_RESULT = "startProcessResult";

  private WorkflowStartActionBuilder() {
  }

  static EventActionConfig buildCurrentRecordRequirementsAction(String processId) {
    return buildRequirementsAction(processId, "context");
  }

  static EventActionConfig buildSavedRecordRequirementsAction(String processId) {
    return buildRequirementsAction(
        processId,
        "context && context.saveResult "
            + "? (context.saveResult.responseData || context.saveResult.data || context.saveResult) "
            + ": context");
  }

  private static EventActionConfig buildRequirementsAction(String processId, String sourceScript) {
    var action = new EventActionConfig();
    var api = new ApiConfig();

    api.setMethod("post");
    api.setUrl("/api/approval-workflow/instance/" + processId + "/start-requirements");
    api.setData(Collections.emptyMap());
    api.setProp("silent", true);
    api.setProp("requestAdaptor", buildFormDataRequestAdaptor(sourceScript));

    action.setActionType("ajax");
    action.setProp("api", api);
    action.setOutputVar(REQUIREMENTS_RESULT);
    return action;
  }

  static EventActionConfig buildRequirementsFailureToastAction() {
    var action = new EventActionConfig();
    var args = new LinkedHashMap<String, Object>();

    args.put("msgType", "error");
    args.put("msg", "获取审批发起信息失败");
    action.setActionType("toast");
    action.setExpression("${" + REQUIREMENTS_RESULT + ".responseStatus !== 0}");
    action.setProp("args", args);
    return action;
  }

  static EventActionConfig buildStopWhenRequirementsFailureAction() {
    var action = new EventActionConfig();
    action.setActionType("stopPropagation");
    action.setExpression("${" + REQUIREMENTS_RESULT + ".responseStatus !== 0}");
    return action;
  }

  static EventActionConfig buildAssigneeDialogAction() {
    var action = new EventActionConfig();
    action.setActionType("dialog");
    action.setExpression("${" + REQUIREMENTS_RESULT + ".responseData.requiresInput}");
    action.setProp("dialog", buildAssigneeDialog());
    action.setProp("waitForAction", true);
    action.setOutputVar(ASSIGNEE_SELECTION);
    return action;
  }

  static EventActionConfig buildStopWhenAssigneeSelectionCanceledAction() {
    var action = new EventActionConfig();
    action.setActionType("stopPropagation");
    action.setExpression("${" + REQUIREMENTS_RESULT + ".responseData.requiresInput && !" + ASSIGNEE_SELECTION + ".confirmed}");
    return action;
  }

  static EventActionConfig buildStartProcessAction(ApiConfig api) {
    var action = new EventActionConfig();
    action.setActionType("ajax");
    action.setProp("api", api);
    action.setOutputVar(START_RESULT);
    return action;
  }

  static ApiConfig buildStartProcessApi(String url) {
    var api = new ApiConfig();

    api.setMethod("post");
    api.setUrl(url);
    api.setData(Collections.emptyMap());
    api.setProp("silent", true);
    api.setProp("requestAdaptor", buildTaskAssigneeRequestAdaptor());
    return api;
  }

  private static String buildFormDataRequestAdaptor(String sourceScript) {
    return "var source = " + sourceScript + ";\n"
        + "var formData = {};\n"
        + "if (source && typeof source === 'object' && !Array.isArray(source)) {\n"
        + "  Object.keys(source).forEach(function(key) { formData[key] = source[key]; });\n"
        + "}\n"
        + "delete formData.processInfo;\n"
        + "api.data = { formData: formData };\n"
        + "return api;";
  }

  private static String buildTaskAssigneeRequestAdaptor() {
    return "var selection = context && context." + ASSIGNEE_SELECTION + ";\n"
        + "var value = Array.isArray(selection && selection.value) ? selection.value[0] : selection && selection.value;\n"
        + "var taskAssignee = value && value.taskAssignee;\n"
        + "api.data = taskAssignee ? { taskAssignee: taskAssignee } : {};\n"
        + "return api;";
  }

  private static CommonContainer buildAssigneeDialog() {
    var source = new SchemaNode();
    source.setType("amis-schema");
    source.setProp("source", "${" + REQUIREMENTS_RESULT + ".responseData.schema}");

    var dialog = new CommonContainer();
    dialog.setType("dialog");
    dialog.setProp("title", "选择审批人");
    dialog.setProp("size", "md");
    dialog.setBody(source);
    dialog.setProp("actions", buildDialogActions());
    return dialog;
  }

  private static List<SchemaNode> buildDialogActions() {
    var actions = new ArrayList<SchemaNode>();

    var cancel = new SchemaNode();
    cancel.setType("action");
    cancel.setProp("actionType", "cancel");
    cancel.setProp("label", "取消");
    actions.add(cancel);

    var confirm = new SchemaNode();
    confirm.setType("action");
    confirm.setProp("actionType", "confirm");
    confirm.setProp("level", "primary");
    confirm.setProp("label", "确认");
    confirm.setProp("mergeData", true);
    actions.add(confirm);

    return actions;
  }
}
