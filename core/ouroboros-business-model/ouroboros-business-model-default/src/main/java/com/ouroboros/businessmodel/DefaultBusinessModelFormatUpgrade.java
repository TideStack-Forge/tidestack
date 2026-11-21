package com.ouroboros.businessmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorMappingPlan;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.custom.CustomConfig;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Json;
import com.ouroboros.webapi.BusinessActionWebApiContract;

/** Explicit conversion of this document only; never copies merged runtime or code definitions. */
public final class DefaultBusinessModelFormatUpgrade implements BusinessModelFormatUpgrade {
  private static final List<String> CONTRACT_FIELDS = List.of("inputSchema", "outputSchema", "binding", "readOnly",
      "precondition", "authorization", "emits");
  private static final List<String> LEGACY_FIELDS = List.of("isGenerateImplementation", "isBindAction", "isCustomLogicFlow",
      "scriptLanguage", "script");

  public DefaultBusinessModelFormatUpgrade() { }


  public Plan prepare(DefaultBusinessModelMeta original, BusinessModelProvider provider) {
    if (!"default".equals(original.getModelType())) { throw new BusinessException("只有默认业务模型支持此格式升级"); }
    if (BusinessModelMetaParser.isCurrent(original)) { throw new BusinessException("当前模型已采用 2.0 格式"); }
    var copy = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(Json.toJsonString(original)).get();
    var sourceModel = new DefaultBusinessModel(copy, provider);
    var actions = new ArrayList<>(copy.getActions());
    var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    actions.forEach(action -> {
      if (action == null || action.getName() == null || !names.add(action.getName())) {
        throw new BusinessException("actions：动作名称缺失或重复，不能升级");
      }
      if (action.getBinding() != null) {
        var source = new BehaviorDefinitionSource(copy.getFullName(), "actions." + action.getName() + ".binding", null);
        action.getBinding().setInputMapping(BusinessBehaviorMappingPlan.upgradeFlowInput(
            action.getBinding().getInputMapping(), action.getInputSchema(), source));
        action.getBinding().setOutputMapping(BusinessBehaviorMappingPlan.upgradeFlowOutput(action.getBinding().getOutputMapping(), source));
      }
    });


    var functions = new ArrayList<Map<String, Object>>();
    var flows = new ArrayList<FlowModelMeta>();
    for (Object raw : copy.getExtraProp(List.class, "custom").orElse(List.of())) {
      var function = stringMap(raw, "custom");
      if (!(function.get("api") instanceof Map<?, ?>)) {
        functions.add(function); continue;
      }
      var api = stringMap(function.get("api"), "custom.api");
      var config = new CustomConfig(function);
      String path = "custom." + config.getName() + ".api";
      var errors = config.getApi().definitionErrors();
      if (!errors.isEmpty()) { throw new BusinessException(path + ": " + String.join(", ", errors)); }
      if (config.getName().isBlank()) { throw new BusinessException(path + ": 功能名称不能为空"); }
      if (config.getApi().isActionBindingEnabled()) {
        if (!names.add(config.getName())) { throw new BusinessException(path + ": 动作名称与已有定义冲突"); }
        if (function.get("binding") != null || api.get("binding") != null) {
          throw new BusinessException(path + ": 旧内联 binding 没有已证明的执行语义，不能自动升级");
        }
        var action = LegacyBusinessActionNormalizer.customDefinition(copy, function);
        actions.add(action);
        api.put("implementationMode", "ACTION"); api.put("action", action.getName());
        var mapping = config.getApi().getActionInputMapping();
        if (mapping.isPresent()) {
          api.put("actionInputMapping", config.getApi().getActionInputMappingFormatVersion() == null
              ? BusinessActionWebApiContract.upgradeMapping(mapping.get(), config.getApi().getUrl()) : mapping.get());
        } else if (config.getApi().getIsEnabled()) {
          throw new BusinessException(path + ".actionInputMapping：已启用的旧动作缺少输入映射");
        }
        if (config.getApi().getIsGenerateImplementation() && !config.getApi().getCustomLogicFlow()) {
          flows.add(ScriptLogicModelPrototype.get(action.getBinding().getFlowName(), sourceModel)
              .configure("modelName", sourceModel.getFields().isEmpty() ? null : copy.getFullName())
              .configure("scriptLanguage", config.getApi().getScriptLanguage())
              .configure("script", config.getApi().getScript()).getModel());
        }
        CONTRACT_FIELDS.forEach(function::remove);
      } else {
        for (String key : CONTRACT_FIELDS) {
          if (meaningful(function.get(key)) || meaningful(api.get(key))) {
            throw new BusinessException(path + "." + key + ": 普通接口中的动作定义不能静默丢弃");
          }
        }
        if (meaningful(api.get("script"))) { throw new BusinessException(path + ".script：未绑定接口的脚本不能静默丢弃"); }
        api.put("implementationMode", "WEB_API");
      }
      CONTRACT_FIELDS.forEach(api::remove);
      LEGACY_FIELDS.forEach(api::remove);
      function.put("api", api); functions.add(function);
    }
    if (copy.getExtraProp("custom").isPresent()) { copy.setExtraProp("custom", functions); }
    copy.setActions(actions); copy.setFormatVersion(BusinessModelMetaParser.CURRENT_VERSION);
    return new Plan(copy, flows);
  }

  private static boolean meaningful(Object value) {
    return value != null && !Boolean.FALSE.equals(value) && !(value instanceof String text && text.isEmpty())
        && !(value instanceof Map<?, ?> map && map.isEmpty()) && !(value instanceof List<?> list && list.isEmpty());
  }

  private static Map<String, Object> stringMap(Object raw, String path) {
    if (!(raw instanceof Map<?, ?> source)) { throw new BusinessException(path + ": 定义必须是对象"); }
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      if (!(key instanceof String name)) { throw new BusinessException(path + ": 属性名必须是字符串"); }
      result.put(name, value);
    });
    return result;
  }
}
