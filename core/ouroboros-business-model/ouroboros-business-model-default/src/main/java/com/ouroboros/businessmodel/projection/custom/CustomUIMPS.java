package com.ouroboros.businessmodel.projection.custom;

import java.util.*;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uischema.schema.ActionNode;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.CommonContainer;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.schema.event.EventActionConfig;

@Priority(10)
public class CustomUIMPS extends AbstractCustomMPS {
  @Override
  protected void doProcess(CustomConfig config, ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();

    var uiConfig = config.getUi();

    // 如果未启用则直接返回
    if (!uiConfig.getIsEnabled()) {
      return;
    }


    var name = config.getName();
    var title = config.getTitle();
    var entryPoint = uiConfig.getEntryPoint();
    var mode = uiConfig.getMode();
    var buttonOrder = uiConfig.getButtonOrder();

    // UI Model
    if (!Arrays.asList("api", "custom").contains(mode)) {
      var path = BusinessModelUtil.buildKebabCasePath(model.getFullName() + "." + name);
      var uiModel = new SimpleUIModel();
      uiModel.setPath(path);
      uiModel.setType("amis");
      uiModel.setExtraProp("schemaId", path);
      uiModel.setTitle(title);
      context.putModel(UIModel.class, path, uiModel);
    }

    var button = buildCustomUiButton(config, model);
    String authorityAction = config.getApi().getIsEnabled() && config.getApi().isActionReference()
        ? config.getApi().getActionName(name) : name;
    button.setProp("hasAuth", config.getApi().getIsEnabled() && config.getApi().isActionReference()
        ? model.getBehavior().executionAuthorityName(authorityAction) : model.getFullName() + "." + name);
    button.setProp("order", buttonOrder);
    if ("toolbar".equalsIgnoreCase(config.getUi().getEntryPoint())) {
      button.setProp("align", config.getUi().getButtonAlign());
    }

    // 添加到索引页的工具栏
    if ("toolbar".equalsIgnoreCase(entryPoint)) {
      context.updateAttribute("INDEX_CUSTOM_TOOLBAR", (List<SchemaNode> list) -> {
        list.add(button);
      }, ArrayList::new);
    }

    // 添加到索引页的操作栏
    if ("operation".equalsIgnoreCase(entryPoint)) {
      context.updateAttribute("INDEX_CUSTOM_OPERATIONS", (List<SchemaNode> list) -> {
        list.add(button);
      }, ArrayList::new);
    }

    // 添加到批量操作
    if ("bulkActions".equalsIgnoreCase(entryPoint)) {
      context.updateAttribute("INDEX_CUSTOM_BULK_ACTIONS", (List<SchemaNode> list) -> {
        list.add(button);
      }, ArrayList::new);
    }

    // 添加到单条操作
    if ("itemActions".equalsIgnoreCase(entryPoint)) {
      context.updateAttribute("INDEX_CUSTOM_ITEM_ACTIONS", (List<SchemaNode> list) -> {
        button.setProp("hiddenOnHover", true);
        list.add(button);
      }, ArrayList::new);
    }
  }

  private SchemaNode buildCustomUiButton(CustomConfig config, DefaultBusinessModel model) {
    var uiConfig = config.getUi();
    if ("custom".equalsIgnoreCase(uiConfig.getMode())) {
      return uiConfig.getButtonSchema();
    }

    var button = new ActionNode();
    var buttonLabel = uiConfig.getButtonLabel().isEmpty() ? config.getTitle() : uiConfig.getButtonLabel();
    var buttonIcon = uiConfig.getButtonIcon().isEmpty() ? config.getIcon() : uiConfig.getButtonIcon();
    var buttonBackgroundColor = uiConfig.getButtonBackgroundColor();
    var buttonForegroundColor = uiConfig.getButtonForegroundColor();
    var mode = uiConfig.getMode();
    var name = config.getName();
    var path = BusinessModelUtil.buildKebabCasePath(model.getFullName() + "." + name);
    var disabledOn = uiConfig.getButtonDisabledOn();
    if (StringUtils.isNotBlank(disabledOn)) {
      button.setProp("disabledOn", disabledOn);
    }

    // 基本属性
    button.setIcon(buttonIcon);
    if (uiConfig.getButtonLabelVisible()) {
      button.setLabel(buttonLabel);
    }
    button.setLevel(uiConfig.getButtonLevel());
    button.setSize(uiConfig.getButtonSize());

    // 自定义颜色
    if (!buttonBackgroundColor.isEmpty() || !buttonForegroundColor.isEmpty()) {
      var style = new HashMap<String, String>();
      if (!buttonBackgroundColor.isEmpty()) {
        style.put("background-color", buttonBackgroundColor);
      }
      if (!buttonForegroundColor.isEmpty()) {
        style.put("color", buttonForegroundColor);
      }
      button.setProp("style", style);
    }

    if ("api".equalsIgnoreCase(mode)) {
      if (!config.getApi().getIsEnabled()) {
        button.setActionType("dialog");
        var dialog = new CommonContainer();
        dialog.setBody("接口未启用");
        dialog.setProp("title", "错误");
        button.setProp("dialog", dialog);
        return button;
      }

      // API 配置
      button.setActionType("ajax");
      var api = new ApiConfig();
      button.setProp("api", api);
      api.setMethod(config.getApi().getHttpMethod());
      api.setUrl("/api" + buildCustomApiUrl(config, model));

      // 确认
      if (uiConfig.getApiConfirm()) {
        button.setConfirmText(uiConfig.getApiConfirmText());
        if (!uiConfig.getApiConfirmTitle().isEmpty()) {
          button.setConfirmTitle(uiConfig.getApiConfirmTitle());
        }
      }

      // 反馈
      if (uiConfig.getApiFeedback()) {
        var feedback = new CommonContainer();
        feedback.setBody(uiConfig.getApiFeedbackBody());
        feedback.setProp("title", uiConfig.getApiFeedbackTitle());
        button.setProp("feedback", feedback);
      }

      // 跳转
      if (uiConfig.getApiRedirect()) {
        button.setProp("redirect", uiConfig.getApiRedirectUrl());
      }

      return button;
    }

    if ("tab".equalsIgnoreCase(mode)) {
      EventActionConfig actionConfig = new EventActionConfig();
      actionConfig.setActionType("openTab");
      actionConfig.setArgs(Collections.singletonMap("url", path));

      button.addOnEvent("click", actionConfig);
      return button;
    }

    var container = new SchemaNode();
    container.setProp("url", path);
    container.setProp("title", config.getTitle());

    if ("dialog".equalsIgnoreCase(mode)) {
      var size = uiConfig.getExtraPropDeep(String.class, "dialog", "size")
          .orElse("md");
      container.setProp("size", size);
      button.setActionType("dialog");
      button.setProp("dialog", container);
      return button;
    }

    if ("drawer".equalsIgnoreCase(mode)) {
      var size = uiConfig.getExtraPropDeep(String.class, "drawer", "size")
          .orElse("md");
      container.setProp("size", size);
      container.setProp("resizable", uiConfig.getExtraPropDeep(Boolean.class, "drawer", "resizable").orElse(false));
      button.setActionType("drawer");
      button.setProp("drawer", container);
      return button;
    }
    // 行为配置
    return button;
  }
}
