package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.uischema.schema.*;
import com.ouroboros.uischema.schema.event.EventActionConfig;
import com.ouroboros.util.Converter;

public class FormUiSchemaModelPrototype extends AbstractDefaultBusinessModelPrototype<FormNode, FormUiSchemaModelPrototype> {
  private FormUiSchemaModelPrototype(DefaultBusinessModel businessModel) {
    super(businessModel);
  }

  public static FormUiSchemaModelPrototype get(DefaultBusinessModel businessModel) {
    return new FormUiSchemaModelPrototype(businessModel);
  }

  @Override
  public FormUiSchemaModelPrototype configure(Map<String, Object> config) {
    this.config.putAll(config);
    return this;
  }

  @Override
  public FormNode getModel() {
    if (!isAuto()) {
      var customSchemaMap = Optional.ofNullable(config.get("schema"))
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .orElseGet(() -> {
            var hashMap = new HashMap<String, Object>();
            hashMap.put("type", "page");
            hashMap.put("body", "请配置自定义页面");
            return hashMap;
          });
      return new FormNode(customSchemaMap);
    }
    preprocessLayout();
    preprocessInitApi();
    preprocessApi();
    preprocessOnEvent();
    preprocessFormItems();
    preprocessActions();

    return PrototypeTemplate.generate("ui-schema/form", config, FormNode.class);
  }

  private void preprocessLayout() {
    if (config.containsKey("formMode")) {
      return;
    }
    var formMode = "normal".equals(getLayoutType()) ? "normal" : "horizontal";
    config.put("formMode", formMode);
  }

  private void preprocessInitApi() {
    if (config.containsKey("initApi")) {
      return;
    }
    var initApi = new ApiConfig();
    var baseUrl = BusinessModelUtil.buildKebabCasePath(businessModel);
    initApi.setMethod("get");
    initApi.setUrl("/api" + baseUrl + "/${id}");
    config.put("initApi", initApi);
  }

  private void preprocessApi() {
    if (config.containsKey("api")) {
      return;
    }
    if (isStatic()) {
      return;
    }
    var api = new ApiConfig();
    var baseUrl = BusinessModelUtil.buildKebabCasePath(businessModel);
    if (isEdit()) {
      api.setMethod("put");
      api.setUrl("/api" + baseUrl + "/${id}");
    } else {
      api.setMethod("post");
      api.setUrl("/api" + baseUrl);
    }
    config.put("api", api);
  }

  private void preprocessOnEvent() {
    // Intentional no-op hook: Subclasses can override to customize onEvent configuration
  }

  // 如果启用了工作流，则在编辑和新建的时候把状态字段过滤掉
  private boolean filterStatusField(SchemaNode node) {
    if (isStatic()) {
      return true;
    }
    if (!isWorkflowEnabled()) {
      return true;
    }
    if ("hidden".equals(node.getType())) {
      return true;
    }
    return !getStatusField().equals(node.getName());
  }

  private void preprocessFormItems() {
    // 先取自定义表单项配置，如果有则直接返回
    var configFields = getConfigFields()
        .stream()
        .filter(this::filterStatusField)
        .collect(Collectors.toList());

    if (!configFields.isEmpty()) {
      config.put("body", configFields);
      return;
    }

    // 如果没有自定义表单项配置，则取默认的表单项
    var formItems = getDefaultFormItemConfig().stream()
        .map(tuple -> {
          var field = tuple._2();
          if (field instanceof BusinessModelRelation) {
            return buildRelationFormItem(tuple.map2(BusinessModelRelation.class::cast));
          }
          if (field instanceof BusinessModelField) {
            return buildFieldFormItem(tuple.map2(BusinessModelField.class::cast));
          }
          return buildCustomFormItem(tuple);
        })
        .filter(Objects::nonNull)
        .filter(this::filterStatusField)
        .collect(Collectors.toList());

    config.put("body", formItems);
  }

  // 目前只有启用了工作流时，才需要维护 actions
  private void preprocessActions() {
    var closeActionType = determineCloseActionType();
    var closeFormAction = createCloseFormAction(closeActionType);
    var baseActions = buildBaseSubmitAndCancel(closeFormAction);
    var submitAction = baseActions._1();
    var cancelAction = baseActions._2();

    if (!isWorkflowEnabled() && !isStatic()) {
      config.put("actions", buildSimpleFormActions(submitAction, cancelAction));
      return;
    }
    if (isStatic()) {
      config.put("actions", buildStaticFormActions(cancelAction));
      return;
    }

    var context = initWorkflowContext();
    configureStatusFlagActions(context);
    configureValidationActions(context);
    configureSaveApiActions(context);
    configureSaveFailureAndBackfillActions(context);
    configureRefreshAndStartProcessActions(context);
    configureToastActions(context);
    configureApiPayloads(context);

    var saveActions = buildSaveActionChain(context);
    var submitActions = buildSubmitActionChain(context, closeFormAction);
    var actions = buildWorkflowButtons(context, submitAction, cancelAction, saveActions, submitActions, closeFormAction);

    config.put("actions", actions);
  }

  private String determineCloseActionType() {
    var showMode = getShowMode();
    return "dialog".equalsIgnoreCase(showMode)
        ? "closeDialog"
        : "drawer".equalsIgnoreCase(showMode)
        ? "closeDrawer"
        : "tab".equalsIgnoreCase(showMode)
        ? "closeTab"
        : "closePage";
  }

  private EventActionConfig createCloseFormAction(String closeActionType) {
    var closeFormAction = new EventActionConfig();
    // 关闭弹窗、抽屉、tab、页面
    closeFormAction.setActionType(closeActionType);
    return closeFormAction;
  }

  private Tuple2<ActionNode, ActionNode> buildBaseSubmitAndCancel(EventActionConfig closeFormAction) {
    var submitAction = new ActionNode();
    var cancelAction = new ActionNode();

    submitAction.setType("button");
    submitAction.setLabel("提交");
    submitAction.setLevel("primary");

    cancelAction.setType("button");
    cancelAction.setLabel("取消");
    cancelAction.setLevel("default");
    cancelAction.addOnEvent("click", closeFormAction);

    return Tuple.of(submitAction, cancelAction);
  }

  private List<ActionNode> buildSimpleFormActions(ActionNode submitAction, ActionNode cancelAction) {
    var actions = new ArrayList<ActionNode>();
    submitAction.setActionType("submit");
    actions.add(submitAction);
    actions.add(cancelAction);
    return actions;
  }

  private List<ActionNode> buildStaticFormActions(ActionNode cancelAction) {
    var actions = new ArrayList<ActionNode>();
    cancelAction.setLabel("关闭");
    actions.add(cancelAction);
    return actions;
  }

  private WorkflowActionContext initWorkflowContext() {
    var context = new WorkflowActionContext();
    context.saveAction = new ActionNode();

    // submitAction，验证 -> 检查 id 为空 post，不为空 put
    context.saveFlagAction = new EventActionConfig();
    context.submitFlagAction = new EventActionConfig();
    context.validate = new EventActionConfig();
    context.stopWhenValidateFailure = new EventActionConfig();
    context.postActionForSubmit = new EventActionConfig();
    context.postActionForSave = new EventActionConfig();
    context.putActionForSubmit = new EventActionConfig();
    context.putActionForSave = new EventActionConfig();
    context.stopWhenSaveFailure = new EventActionConfig();
    context.setValueAction = new EventActionConfig();
    context.saveFailureToastAction = new EventActionConfig();
    context.saveSuccessToastAction = new EventActionConfig();
    context.startRequirementsAction = new EventActionConfig();
    context.startRequirementsFailureToastAction = new EventActionConfig();
    context.stopWhenStartRequirementsFailure = new EventActionConfig();
    context.assigneeDialogAction = new EventActionConfig();
    context.stopWhenAssigneeSelectionCanceled = new EventActionConfig();
    context.startProcessAction = new EventActionConfig();
    context.stopWhenStartProcessFailure = new EventActionConfig();
    context.refreshAction = new EventActionConfig();
    context.startProcessFailureToastAction = new EventActionConfig();
    context.startProcessSuccessToastAction = new EventActionConfig();

    context.postApiForSubmit = new ApiConfig();
    context.postApiForSave = new ApiConfig();
    context.putApiForSubmit = new ApiConfig();
    context.putApiForSave = new ApiConfig();
    context.startProcessApi = new ApiConfig();
    return context;
  }

  private void configureStatusFlagActions(WorkflowActionContext context) {
    // 保存前设置状态字段
    var saveFlagArgs = new LinkedHashMap<String, Object>();
    context.saveFlagAction.setActionType("setEventData");
    saveFlagArgs.put("key", "statusValue");
    saveFlagArgs.put("value", getDraftStatus());
    context.saveFlagAction.setArgs(saveFlagArgs);

    // 提交前设置状态字段
    var submitFlagArgs = new LinkedHashMap<String, Object>();
    context.submitFlagAction.setActionType("setEventData");
    submitFlagArgs.put("key", "statusValue");
    submitFlagArgs.put("value", getSubmittedStatus());
    context.submitFlagAction.setArgs(submitFlagArgs);
  }

  private void configureValidationActions(WorkflowActionContext context) {
    // 验证表单
    context.validate.setActionType("validate");
    context.validate.setComponentId("default-add-edit-form");

    // 验证失败停止动作
    context.stopWhenValidateFailure.setActionType("stopPropagation");
    context.stopWhenValidateFailure.setExpression("${validateResult.error}");
  }

  private void configureSaveApiActions(WorkflowActionContext context) {
    // 新建接口
    context.postActionForSubmit.setActionType("ajax");
    context.postActionForSubmit.setProp("api", context.postApiForSubmit);
    context.postActionForSubmit.setExpression("${!validateResult.payload.id}");
    context.postActionForSubmit.setProp("outputVar", "saveResult");

    context.postActionForSave.setActionType("ajax");
    context.postActionForSave.setProp("api", context.postApiForSave);
    context.postActionForSave.setExpression("${!id}");
    context.postActionForSave.setProp("outputVar", "saveResult");

    // 修改接口
    context.putActionForSubmit.setActionType("ajax");
    context.putActionForSubmit.setProp("api", context.putApiForSubmit);
    context.putActionForSubmit.setExpression("${validateResult.payload.id}");
    context.putActionForSubmit.setProp("outputVar", "saveResult");

    context.putActionForSave.setActionType("ajax");
    context.putActionForSave.setProp("api", context.putApiForSave);
    context.putActionForSave.setExpression("${id}");
    context.putActionForSave.setProp("outputVar", "saveResult");
  }

  private void configureSaveFailureAndBackfillActions(WorkflowActionContext context) {
    // 保存失败停止动作
    context.stopWhenSaveFailure.setActionType("stopPropagation");
    context.stopWhenSaveFailure.setExpression("${saveResult.responseStatus !== 0}");

    // 回填数据
    context.setValueAction.setActionType("setValue");
    context.setValueAction.setComponentId("default-add-edit-form");
    context.setValueAction.setProp("args", Collections.singletonMap("value", "${saveResult.responseData}"));
  }

  private void configureRefreshAndStartProcessActions(WorkflowActionContext context) {
    // 刷新数据
    context.refreshAction.setActionType("reload");
    context.refreshAction.setComponentId("default-crud-list");

    context.startRequirementsAction =
        WorkflowStartActionBuilder.buildSavedRecordRequirementsAction(getWorkflowProcessId());
    context.startRequirementsFailureToastAction = WorkflowStartActionBuilder.buildRequirementsFailureToastAction();
    context.stopWhenStartRequirementsFailure = WorkflowStartActionBuilder.buildStopWhenRequirementsFailureAction();
    context.assigneeDialogAction = WorkflowStartActionBuilder.buildAssigneeDialogAction();
    context.stopWhenAssigneeSelectionCanceled = WorkflowStartActionBuilder.buildStopWhenAssigneeSelectionCanceledAction();

    // 启动流程
    context.startProcessAction = WorkflowStartActionBuilder.buildStartProcessAction(context.startProcessApi);

    context.stopWhenStartProcessFailure.setActionType("stopPropagation");
    context.stopWhenStartProcessFailure.setExpression("${startProcessResult.responseStatus !== 0}");
  }

  private void configureToastActions(WorkflowActionContext context) {
    // 失败信息
    context.saveFailureToastAction.setActionType("toast");
    context.saveFailureToastAction.setExpression(context.stopWhenSaveFailure.getExpression());
    var saveFailureArgs = new HashMap<>();
    saveFailureArgs.put("msgType", "error");
    saveFailureArgs.put("msg", "保存失败");
    context.saveFailureToastAction.setProp("args", saveFailureArgs);

    // 提示信息
    context.saveSuccessToastAction.setActionType("toast");
    var saveSuccessArgs = new HashMap<>();
    saveSuccessArgs.put("msgType", "success");
    saveSuccessArgs.put("msg", "保存成功");
    context.saveSuccessToastAction.setProp("args", saveSuccessArgs);

    // 启动流程失败
    context.startProcessFailureToastAction.setActionType("toast");
    context.startProcessFailureToastAction.setExpression(context.stopWhenStartProcessFailure.getExpression());
    var startProcessFailureArgs = new HashMap<>();
    startProcessFailureArgs.put("msgType", "error");
    startProcessFailureArgs.put("msg", "发起审批失败");
    context.startProcessFailureToastAction.setProp("args", startProcessFailureArgs);

    // 启动流程成功
    context.startProcessSuccessToastAction.setActionType("toast");
    var startProcessSuccessArgs = new HashMap<>();
    startProcessSuccessArgs.put("msgType", "success");
    startProcessSuccessArgs.put("msg", "成功发起审批");
    context.startProcessSuccessToastAction.setProp("args", startProcessSuccessArgs);
  }

  private void configureApiPayloads(WorkflowActionContext context) {
    var saveDataForSubmit = new LinkedHashMap<String, String>();
    var saveDataForSave = new LinkedHashMap<String, String>();

    saveDataForSubmit.put("&", "${validateResult.payload}");
    saveDataForSubmit.put(getStatusField(), "${statusValue}");

    saveDataForSave.put("&", "$$");
    saveDataForSave.put(getStatusField(), "${statusValue}");

    context.postApiForSubmit.setMethod("post");
    context.postApiForSubmit.setUrl("/api" + BusinessModelUtil.buildKebabCasePath(businessModel));
    context.postApiForSubmit.setData(saveDataForSubmit);
    context.postApiForSubmit.setProp("silent", true);

    context.postApiForSave.putProps(context.postApiForSubmit.getProps());
    context.postApiForSave.setData(saveDataForSave);

    context.putApiForSubmit.setMethod("put");
    context.putApiForSubmit.setUrl("/api" + BusinessModelUtil.buildKebabCasePath(businessModel) + "/${validateResult.payload.id}");
    context.putApiForSubmit.setData(saveDataForSubmit);
    context.putApiForSubmit.setProp("silent", true);

    context.putApiForSave.putProps(context.putApiForSubmit.getProps());
    context.putApiForSave.setUrl("/api" + BusinessModelUtil.buildKebabCasePath(businessModel) + "/${id}");
    context.putApiForSave.setData(saveDataForSave);

    context.startProcessApi.putProps(WorkflowStartActionBuilder
        .buildStartProcessApi("/api" + BusinessModelUtil.buildKebabCasePath(businessModel) + "/${saveResult.id}/start-process")
        .getProps());
  }

  private List<EventActionConfig> buildSaveActionChain(WorkflowActionContext context) {
    var saveActions = new ArrayList<EventActionConfig>();
    // 保存的动作列表
    saveActions.add(context.saveFlagAction);
    saveActions.add(context.putActionForSave);
    saveActions.add(context.postActionForSave);
    saveActions.add(context.saveFailureToastAction);
    saveActions.add(context.stopWhenSaveFailure);
    saveActions.add(context.setValueAction);
    saveActions.add(context.saveSuccessToastAction);
    saveActions.add(context.refreshAction);
    return saveActions;
  }

  private List<EventActionConfig> buildSubmitActionChain(WorkflowActionContext context,
                                                         EventActionConfig closeFormAction) {
    var submitActions = new ArrayList<EventActionConfig>();
    // 提交动作先为事件上下文添加提交标识，再添加所有保存的动作，然后再根据配置决定是否发起流程
    submitActions.add(context.submitFlagAction);
    submitActions.add(context.validate);
    submitActions.add(context.stopWhenValidateFailure);
    submitActions.add(context.putActionForSubmit);
    submitActions.add(context.postActionForSubmit);
    submitActions.add(context.saveFailureToastAction);
    submitActions.add(context.stopWhenSaveFailure);
    submitActions.add(context.setValueAction);
    submitActions.add(context.saveSuccessToastAction);
    //  如果为自动触发则添加启动流程的动作
    if ("auto".equals(getStartMode())) {
      submitActions.add(context.startRequirementsAction);
      submitActions.add(context.startRequirementsFailureToastAction);
      submitActions.add(context.stopWhenStartRequirementsFailure);
      submitActions.add(context.assigneeDialogAction);
      submitActions.add(context.stopWhenAssigneeSelectionCanceled);
      submitActions.add(context.startProcessAction);
      submitActions.add(context.startProcessFailureToastAction);
      submitActions.add(context.stopWhenStartProcessFailure);
      submitActions.add(context.startProcessSuccessToastAction);
    }
    submitActions.add(context.refreshAction);
    // 关闭弹窗
    submitActions.add(closeFormAction);
    return submitActions;
  }

  private List<ActionNode> buildWorkflowButtons(WorkflowActionContext context,
                                                ActionNode submitAction,
                                                ActionNode cancelAction,
                                                List<EventActionConfig> saveActions,
                                                List<EventActionConfig> submitActions,
                                                EventActionConfig closeFormAction) {
    var actions = new ArrayList<ActionNode>();

    context.saveAction.setType("button");
    context.saveAction.setLabel("保存");
    context.saveAction.setLevel("success");
    context.saveAction.addOnEvent("click", saveActions);
    context.saveAction.setProp("disabledOnAction", true);
    actions.add(context.saveAction);

    submitAction.addOnEvent("click", submitActions);
    submitAction.setProp("disabledOnAction", true);
    actions.add(submitAction);

    cancelAction.addOnEvent("click", closeFormAction);
    actions.add(cancelAction);
    return actions;
  }

  private static class WorkflowActionContext {
    private ActionNode saveAction;

    private EventActionConfig saveFlagAction;
    private EventActionConfig submitFlagAction;
    private EventActionConfig validate;
    private EventActionConfig stopWhenValidateFailure;
    private EventActionConfig postActionForSubmit;
    private EventActionConfig postActionForSave;
    private EventActionConfig putActionForSubmit;
    private EventActionConfig putActionForSave;
    private EventActionConfig stopWhenSaveFailure;
    private EventActionConfig setValueAction;
    private EventActionConfig saveFailureToastAction;
    private EventActionConfig saveSuccessToastAction;
    private EventActionConfig startRequirementsAction;
    private EventActionConfig startRequirementsFailureToastAction;
    private EventActionConfig stopWhenStartRequirementsFailure;
    private EventActionConfig assigneeDialogAction;
    private EventActionConfig stopWhenAssigneeSelectionCanceled;
    private EventActionConfig startProcessAction;
    private EventActionConfig stopWhenStartProcessFailure;
    private EventActionConfig refreshAction;
    private EventActionConfig startProcessFailureToastAction;
    private EventActionConfig startProcessSuccessToastAction;

    private ApiConfig postApiForSubmit;
    private ApiConfig postApiForSave;
    private ApiConfig putApiForSubmit;
    private ApiConfig putApiForSave;
    private ApiConfig startProcessApi;
  }

  private SchemaNode buildFieldFormItem(Tuple2<String, BusinessModelField> fieldTuple) {
    var label = fieldTuple._1();
    var field = fieldTuple._2();

    // 如果是静态表单或者是在编辑状态中的主键，则使用静态表单项
    var isStatic = isStatic() || field.getIsPrimaryKey() && isEdit();

    var formItem = isStatic
        ? UiSchemaUtils.buildDisplayFormItem(field, label)
        : UiSchemaUtils.buildInputFormItem(field, label);

    formItem.setName(getFieldNamePrefix() + formItem.getName());

    return formItem;
  }

  private SchemaNode buildCustomFormItem(Tuple2<String, Object> fieldTuple) {
    var label = fieldTuple._1();
    var field = fieldTuple._2();
    if (field instanceof SchemaNode) {
      return (SchemaNode) field;
    }
    if (field instanceof Map itemConfig) {
      var fieldSchemaMap = Optional.ofNullable(itemConfig.get("schema"))
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .orElseGet(() -> {
            var hashMap = new HashMap<String, Object>();
            hashMap.put("type", "tpl");
            hashMap.put("body", "自定义表单项，请配置 schema");
            return hashMap;
          });
      var fieldSchema = new SchemaNode(fieldSchemaMap);
      fieldSchema.setProp("label", label);
      return fieldSchema;
    }
    var errorSchema = new TplNode("不支持的表单项类型 " + field.getClass().getSimpleName() + "，请检查模型配置");
    errorSchema.setProp("label", label);
    return errorSchema;
  }

  private SchemaNode buildRelationFormItem(Tuple2<String, BusinessModelRelation> fieldTuple) {
    var label = StringUtils.isEmpty(fieldTuple._1()) ? fieldTuple._2().getTitle() : fieldTuple._1();
    var relation = fieldTuple._2();

    if ("master".equalsIgnoreCase(relation.getType()) || "reference".equalsIgnoreCase(relation.getType())) {
      return buildRefRelationUiSchema(fieldTuple.map1(s -> label));
    }
    if ("child".equalsIgnoreCase(relation.getType())) {
      return buildCollectionRelationUiSchema(fieldTuple.map1(s -> label));
    }

    var errorSchema = new TplNode("不支持的关系类型 " + relation.getType() + "，请检查模型配置");
    errorSchema.setProp("label", label);
    return errorSchema;
  }

  private SchemaNode buildRefRelationUiSchema(Tuple2<String, BusinessModelRelation> relationTuple) {
    return relationTuple.apply(isStatic() ? this::buildReferenceModelDisplayFormItem : this::buildReferenceModelInputFormItem);
  }

  private SchemaNode buildReferenceModelDisplayFormItem(String label, BusinessModelRelation relation) {
    var displayType = relation.getExtraProp(String.class, "displayType", "fieldSet");
    if ("fieldSet".equalsIgnoreCase(displayType) && getFieldNamePrefix().isEmpty()) {
      return buildRefDetailFieldSet(label, relation);
    }
    if ("formItem".equalsIgnoreCase(displayType)
        || "fieldSet".equalsIgnoreCase(displayType)
        && !getFieldNamePrefix().isEmpty()) {
      return buildRefFormItem(label, relation);
    }
    return new TplNode("不支持的 displayType 类型 " + displayType + "，请检查模型配置");
  }

  private SchemaNode buildRefFormItem(String label, BusinessModelRelation relation) {
    var displayItem = UiSchemaUtils.buildPicker(relation);
    var referenceKey = Optional.ofNullable(relation.getReferenceKey())
        .filter(StringUtils::isNotBlank)
        .orElse("id");
    displayItem.setProp("static", true);
    displayItem.setProp("placeholder", "-");
    displayItem.setName(getFieldNamePrefix() + relation.getName());

    var actionItem = new ActionNode();
    var dialog = new CommonContainer();
    var schema = new SchemaNode();
    var dialogData = new HashMap<String, Object>();
    actionItem.setType("button");
    actionItem.setIcon("fa fa-eye");
    actionItem.setActionType("dialog");
    actionItem.setProp("dialog", dialog);
    actionItem.setLevel("link");
    actionItem.setVisibleOn("${ !!" + displayItem.getName() + " }");
    dialog.setProp("title", "查看" + label);
    dialog.setProp("data", dialogData);
    dialog.setBody(schema);
    dialog.setProp("size", "md");
    schema.setType("amis-schema");
    schema.setProp("schemaId", BusinessModelUtil.buildKebabCasePath(relation.getRelatedModel()) + "/detail");

    if ("id".equalsIgnoreCase(referenceKey)) {
      dialogData.put("id", "${ ISTYPE(relationName, 'plain-object') ? relationName.id : relationName }"
          .replaceAll("relationName", getFieldNamePrefix() + relation.getName()));
    } else {
      dialogData.put(referenceKey, "${ ISTYPE(relationName, 'plain-object') ? relationName.referenceKey : relationName  }"
          .replaceAll("relationName", getFieldNamePrefix() + relation.getName())
          .replaceAll("referenceKey", referenceKey));
      var overrideApi = new ApiConfig();
      var serviceApiBaseUrl = BusinessModelUtil.buildKebabCasePath(relation.getRelatedModel());
      var serviceApiUrl = "/api" + serviceApiBaseUrl + ("/?referenceKey=${ referenceKey }".replaceAll("referenceKey", referenceKey));
      overrideApi.setMethod("get");
      overrideApi.setUrl(serviceApiUrl);
      overrideApi.setResponseData(Collections.singletonMap("&", "${ items[0] }"));
      schema.setProp("initApi", overrideApi);
    }

    var inputGroup = new FormItemNode();
    inputGroup.setType("input-group");
    inputGroup.setProp("body", Arrays.asList(displayItem, actionItem));
    inputGroup.setLabel(label);

    return inputGroup;
  }

  private SchemaNode buildRefDetailFieldSet(String label, BusinessModelRelation relation) {
    var relatedModel = relation.getRelatedModel();
    var relationName = getFieldNamePrefix() + relation.getName();
    var relationTitle = relation.getTitle();
    var referenceKey = Optional.ofNullable(relation.getReferenceKey())
        .filter(StringUtils::isNotBlank)
        .orElse("id");

    var fieldSet = new CommonContainer();
    var initApi = new ApiConfig();

    fieldSet.setType("fieldSet");
    fieldSet.setProp("title", StringUtils.isNotEmpty(label) ? label : relationTitle);
    fieldSet.setProp("collapsable", true);
    fieldSet.setClassName("w-full tide-default-fieldset");

    initApi.setMethod("get");
    var serviceApiBaseUrl = BusinessModelUtil.buildKebabCasePath(relatedModel);
    if ("id".equalsIgnoreCase(referenceKey)) {
      var initApiUrl = "/api" + serviceApiBaseUrl + "/$__super." + relationName;
      var initApiResponseData = Collections.singletonMap(relationName, "$$");
      initApi.setUrl(initApiUrl);
      initApi.setResponseData(initApiResponseData);
    } else {
      var initApiUrl = "/api" + serviceApiBaseUrl;
      var initApiResponseData = Collections.singletonMap(relationName, "${items[0]}");
      var initApiData = Collections.singletonMap(referenceKey, "${__super." + relationName + "}");
      initApi.setUrl(initApiUrl);
      initApi.setData(initApiData);
      initApi.setResponseData(initApiResponseData);
    }

    // 剔除关联字段，避免循环引用
    var omitRelationNames = relatedModel.getRelations()
        .stream()
        .filter(r -> r.getRelatedModelName().equals(businessModel.getFullName()))
        .map(BusinessModelRelation::getName)
        .collect(Collectors.toList());

    var subForm = get(relatedModel)
        .configure("isAuto", true)
        .configure("showMode", "embed")
        .configure("layoutType", config.get("layoutType"))
        .configure("columnCount", config.get("columnCount"))
        .configure("labelWidth", config.get("labelWidth"))
        .configure("static", true)
        .configure("api", null)
        .configure("initApi", null)
        .configure("fieldNamePrefix", relationName + ".")
        .configure("showRelatedTable", false)
        .configure("omit", omitRelationNames)
        .getModel();

    subForm.setActions(Collections.emptyList());
    subForm.setWrapWithPanel(false);
    subForm.setInitApi(initApi);
    subForm.setCanAccessSuperData(false);
    subForm.setInitFetchOn("${ !ISTYPE(relationName, 'plain-object') }"
        .replaceAll("relationName", relationName));

    fieldSet.setBody(subForm);
    fieldSet.setVisibleOn("${ " + relationName + " }");

    return fieldSet;
  }

  private SchemaNode buildReferenceModelInputFormItem(String label, BusinessModelRelation relation) {
    var schema = UiSchemaUtils.buildRelationFormItem(relation);
    schema.setProp("label", label);
    schema.setProp("placeholder", "请选择" + label);
    return schema;
  }

  private SchemaNode buildCollectionRelationUiSchema(Tuple2<String, BusinessModelRelation> fieldTuple) {
    if (isStatic() && !isShowRelatedTable()) {
      return null;
    }
    return fieldTuple.apply(isStatic() ? this::buildChildTable : this::buildInputTable);
  }

  private SchemaNode buildInputTable(String label, BusinessModelRelation relation) {
    var relatedModel = relation.getRelatedModel();
    var name = relation.getName();
    var relationTitle = relation.getTitle();
    var referenceKey = Optional.ofNullable(relation.getReferenceKey()).orElse(businessModel.getName());
    var fieldSet = new CommonContainer();
    var customInputFields = relation.getExtraPropDeep(Boolean.class, "subTable", "isCustomInputFields")
        .orElse(false)
        ? relation.getExtraPropDeep(List.class, "subTable", "customInputFields")
        .map(fieldList -> ((List<Object>) fieldList).stream().map(field -> {
          if (!(field instanceof Map<?, ?> fm)) {
            return field;
          }
          var newFm = new HashMap<Object, Object>(fm);
          newFm.put("inputProps", fm.get("columnProps"));
          return newFm;
        }).collect(Collectors.toList()))
        .orElseGet(Collections::emptyList)
        : Collections.emptyList();

    // TODO: 如果没有 submitTogether 则用 CRUD 取代 inputTable
    //       这块逻辑最好移出去，放到 buildCollectionRelationUiSchema 中
    if (!relation.getSubmitTogether() && !isEdit()) {
      return null;
    }

    var inputTable = InputTableUiSchemaModelPrototype.get(relatedModel)
        .configure("name", name)
        .configure("referenceKey", referenceKey)
        .configure("isEdit", isEdit())
        .configure("fields", customInputFields)
        .getModel();

    fieldSet.setType("fieldSet");
    fieldSet.setClassName("w-full");
    fieldSet.setProp("mode", "normal");
    fieldSet.setProp("title", StringUtils.isNotEmpty(label) ? label : relationTitle);
    if (isEdit()) {
      var service = new CommonContainer();
      var serviceApi = new ApiConfig();
      var serviceApiBaseUrl = BusinessModelUtil.buildKebabCasePath(relatedModel);
      var serviceApiUrl = "/api" + serviceApiBaseUrl;
      var serviceApiSendOn = String.format("${ AND(!ISTYPE(%s, 'array'), !!%s) }", name, relation.getKey());
      var serviceApiResponseData = Collections.singletonMap(name, "${ items }");
      var serviceApiData = new LinkedHashMap<String, Object>();
      serviceApiData.put(referenceKey, "${ " + relation.getKey() + " }");
      serviceApi.setData(serviceApiData);
      serviceApi.setMethod("get");
      serviceApi.setUrl(serviceApiUrl);
      serviceApi.setSendOn(serviceApiSendOn);
      serviceApi.setResponseData(serviceApiResponseData);
      serviceApi.setData(serviceApiData);
      service.setType("service");
      service.setProp("api", serviceApi);
      service.setBody(inputTable);
      service.setVisibleOn(String.format("${ OR(%s, %s) }", name, relation.getKey()));
      fieldSet.setBody(service);
    } else {
      fieldSet.setBody(inputTable);
    }
    return fieldSet;
  }

  private SchemaNode buildChildTable(String label, BusinessModelRelation relation) {
    var relatedModel = relation.getRelatedModel();
    var referenceKey = relation.getReferenceKey();
    var relationTitle = relation.getTitle();
    var additionalParamKey = referenceKey != null ? referenceKey : businessModel.getName();
    var fieldSet = new CommonContainer();
    var customDisplayColumns = relation
        .getExtraPropDeep(Boolean.class, "subTable", "isCustomDisplayColumns").orElse(false)
        ? relation.getExtraPropDeep(List.class, "subTable", "customDisplayColumns").orElseGet(Collections::emptyList)
        : Collections.emptyList();

    var omit = Collections.singletonList(additionalParamKey);

    var crud = CrudUiSchemaModelPrototype.get(relatedModel)
        .configure(relatedModel.getExtraPropDeep(Map.class, "views", "index").orElseGet(Collections::emptyMap))
        .configure("isAuto", true)
        .configure("additionalParams", Collections.singletonMap(additionalParamKey, "${ " + relation.getKey() + " }"))
        .configure("showOperations", false)
        .configure("showHeaderToolbarActions", false)
        .configure("showFilters", false)
        .configure("isExportCurrentPageEnabled", false)
        .configure("isExportAllEnabled", false)
        .configure("omit", omit)
        .configure("displayColumns", customDisplayColumns)
        .getModel();

    crud.setProp("filter", null);
    crud.setProp("loadDataOnce", true);

    fieldSet.setType("fieldSet");
    fieldSet.setClassName("w-full");
    fieldSet.setProp("title", StringUtils.isNotEmpty(label) ? label : relationTitle);
    fieldSet.setBody(crud);

    return fieldSet;
  }

  private Boolean isAuto() {
    return Optional.ofNullable(config.getOrDefault("isAuto", true))
        .map(Converter::toBoolean)
        .orElse(true);
  }

  private Boolean isEdit() {
    return Optional.ofNullable(config.getOrDefault("isEdit", false))
        .map(Converter::toBoolean)
        .orElse(false);
  }

  private Boolean isStatic() {
    return Optional.ofNullable(config.get("static"))
        .map(Converter::toBoolean)
        .orElse(false);
  }

  private String getShowMode() {
    return Optional.ofNullable(config.get("showMode"))
        .map(Converter::toString)
        .orElse("dialog");
  }

  private Boolean isNew() {
    return !isStatic() && !isEdit();
  }

  private String getLayoutType() {
    return Optional.ofNullable(config.getOrDefault("layoutType", "horizontal"))
        .map(Converter::toString)
        .orElse("horizontal");
  }

  private String getFieldNamePrefix() {
    return Optional.ofNullable(config.get("fieldNamePrefix"))
        .map(Converter::toString)
        .orElse("");
  }

  private Boolean isShowRelatedTable() {
    return Optional.ofNullable(config.getOrDefault("showRelatedTable", true))
        .map(Converter::toBoolean)
        .orElse(true);
  }

  private Boolean isWorkflowEnabled() {
    return Optional.ofNullable(config.get("workflowEnabled"))
        .map(Converter::toBoolean)
        .orElse(false);
  }

  private String getStatusField() {
    return Optional.ofNullable(config.get("statusField"))
        .map(Converter::toString)
        .orElse("");
  }

  private String getWorkflowProcessId() {
    return businessModel.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse("");
  }

  private String getDraftStatus() {
    return Optional.ofNullable(config.get("draftStatus"))
        .map(Converter::toString)
        .orElse("");
  }

  private String getSubmittedStatus() {
    return Optional.ofNullable(config.get("submittedStatus"))
        .map(Converter::toString)
        .orElse("");
  }

  private String getStartMode() {
    return Optional.ofNullable(config.get("startMode"))
        .map(Converter::toString)
        .orElse("auto");
  }


  @SuppressWarnings("unchecked")
  private List<SchemaNode> getConfigFields() {
    return Optional.ofNullable(config.get("fields"))
        .map(l -> (l instanceof List<?> list) ? list : null)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(Objects::nonNull)
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(filterOmit(map -> map.get("name") instanceof String name ? name : null))
        .map(m -> {
          var itemConfig = (Map<String, Object>) m;
          if (itemConfig.isEmpty()) {
            return null;
          }
          var title = Optional.ofNullable(itemConfig.get("title")).map(Converter::toString).filter(StringUtils::isNotBlank).orElse(null);
          var name = Optional.ofNullable(itemConfig.get("name")).map(Converter::toString).orElse(null);
          var schema = Optional.ofNullable(itemConfig.get("schema"))
              .filter(Map.class::isInstance)
              .map(Map.class::cast)
              .map(map -> {
                var schemaNode = new SchemaNode();
                schemaNode.putProps(map);
                if (StringUtils.isBlank(schemaNode.getName())) {
                  schemaNode.setName(getFieldNamePrefix() + name);
                } else {
                  schemaNode.setName(getFieldNamePrefix() + schemaNode.getName());
                }
                return schemaNode;
              }).orElse(null);
          var inputProps = Optional.ofNullable(m.get("inputProps"))
              .filter(Map.class::isInstance)
              .map(props -> (Map<?, ?>) props)
              .orElseGet(Collections::emptyMap)
              .entrySet()
              .stream()
              .filter(e -> e.getKey() instanceof String)
              .filter(e -> e.getValue() != null && (!(e.getValue() instanceof String sv) || !sv.isEmpty()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2));

          var type = Optional.ofNullable(itemConfig.get("type")).map(Converter::toString).orElse(null);

          if ("field".equalsIgnoreCase(type)) {
            var field = businessModel.getField(name).orElse(null);
            if (field == null) {
              return null;
            }
            if (schema == null) {
              schema = buildFieldFormItem(Tuple.of(Optional.ofNullable(title).orElseGet(field::getTitle), field));
            }
          } else if ("relation".equalsIgnoreCase(type)) {
            var relation = businessModel.getRelation(name).orElse(null);
            if (relation == null) {
              return null;
            }
            if (schema == null) {
              schema = buildRelationFormItem(Tuple.of(Optional.ofNullable(title).orElseGet(relation::getTitle), relation));
            }
          } else if ("builtin".equalsIgnoreCase(type)) {
            if (schema == null) {
              schema = new FormItemNode();
              schema.setType("static-text");
            }
            ;
            schema.setName(getFieldNamePrefix() + name);
          } else if ("placeholder".equalsIgnoreCase(type)) {
            title = null;
            schema = new SchemaNode();
            schema.setType("static-text");
          } else if ("divider".equalsIgnoreCase(type)) {
            schema = new SchemaNode();
            schema.setType("divider");
          } else {
            schema = new SchemaNode();
          }
          if (schema == null) {
            return null;
          }
          if (StringUtils.isNotEmpty(title) && schema.getProp(String.class, "label", "").isEmpty()) {
            schema.setProp("label", title);
          }
          schema.putProps((Map<String, Object>) inputProps);
          return schema;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private List<Tuple2<String, Object>> getDefaultFormItemConfig() {
    var masterOrRefRelationTypes = Arrays.asList("master", "reference");

    var modelRefRelations = businessModel.getRelations()
        .stream()
        .filter(r -> masterOrRefRelationTypes.contains(r.getType()))
        .filter(filterOmit(BusinessModelRelation::getName))
        .<Tuple2<String, Object>>map(r -> Tuple.of(r.getTitle(), r));
    var childRelations = businessModel.getRelations()
        .stream()
        .filter(r -> "child".contains(r.getType()))
        .filter(filterOmit(BusinessModelRelation::getName))
        .<Tuple2<String, Object>>map(r -> Tuple.of(r.getTitle(), r));
    var fields = businessModel.getFields()
        .stream()
        .filter(f -> !f.getIsPrimaryKey())
        .filter(filterOmit(BusinessModelField::getName))
        .<Tuple2<String, Object>>map(f -> Tuple.of(f.getTitle(), f));
    return Stream.of(modelRefRelations, fields, childRelations)
        .flatMap(Function.identity())
        .collect(Collectors.toList());
  }

  private <T> Predicate<T> filterOmit(Function<T, String> nameGetter) {
    var omit = Optional.ofNullable(config.get("omit"))
        .map(l -> (l instanceof List<?> list) ? list : null)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(Converter::toString)
        .collect(Collectors.toList());
    if (omit.isEmpty()) {
      return t -> true;
    }
    return t -> !omit.contains(nameGetter.apply(t));
  }
}
