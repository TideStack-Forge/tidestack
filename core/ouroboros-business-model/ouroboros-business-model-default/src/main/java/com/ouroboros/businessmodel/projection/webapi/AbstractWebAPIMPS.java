package com.ouroboros.businessmodel.projection.webapi;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.support.BusinessModelOpenAPISupport;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;

public abstract class AbstractWebAPIMPS extends AbstractDefaultBusinessModelProcessStep {
  final static String LOGIC_FLOW_FIELD_NAME = "logicFlow";
  final static String WEBAPIS_CONFIG_FIELD_NAME = "apis";

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected Map<String, Object> getWebApiConfig(DefaultBusinessModel model, String apiName) {
    return (Map<String, Object>) model.getExtraProp(Map.class, WEBAPIS_CONFIG_FIELD_NAME)
        .map(webapiConfig -> webapiConfig.get(apiName))
        .map(config -> config instanceof Map configMap ? configMap : null)
        .filter(configMap -> configMap.keySet().stream().allMatch(String.class::isInstance))
        .orElseGet(Collections::emptyMap);
  }

  protected String getPrimaryKeyName(DefaultBusinessModel model) {
    return model.getFields().stream()
        .filter(BusinessModelField::getIsPrimaryKey)
        .findFirst()
        .map(BusinessModelField::getName)
        .orElse("id");
  }

  private static Map<String, Object> actionInputMapping(String action, String idName) {
    return switch (action) {
      case "add" -> Map.of("data", "${ request.body }");
      case "getOne", "delete" -> Map.of("params.record.id", "${ request.pathVariables['" + idName + "'] }");
      case "update" -> Map.of("params.record.id", "${ request.pathVariables['" + idName + "'] }", "data", "${ request.body }");
      case "search" -> Map.of("params.query", "${ query }");
      default -> Map.of();
    };
  }

  /**
   * 生成WebAPI的模型处理器
   *
   * @param configName       配置名称
   * @param enabledCondition 启用条件
   * @param httpMethod       HTTP方法
   * @param pathBuilder      路径构建器
   * @param logicFlowName    逻辑流名称
   * @return 模型处理器
   */
  protected Consumer<ModelProjectionPipelineContext<DefaultBusinessModel>> getWebApiModelProcessor(
      String configName, Predicate<Map<String, Object>> enabledCondition, String httpMethod, BiFunction<String, String, String> pathBuilder, String logicFlowName) {
    return context -> {
      if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
        return;
      }
      final var model = context.getSourceModel();
      final var addWebApiConfig = getWebApiConfig(model, configName);
      if (!enabledCondition.test(addWebApiConfig)) {
        return;
      }
      // 生成Web API模型
      final var apiPathPrefix = BusinessModelUtil.buildKebabCasePath(model);
      final var apiPath = pathBuilder.apply(apiPathPrefix, getPrimaryKeyName(model));
      final var webApiBuilder = SimpleWebAPIModel.builder()
          .api(httpMethod, apiPath)
          .scope(WebAPIScope.INTERNAL)
          .handlerType(HandlerType.BUSINESS_ACTION)
          .extraProp("formatVersion", "2.0")
          .extraProp("businessModel", model.getFullName())
          .extraProp("action", logicFlowName)
          .extraProp("actionInputMapping", actionInputMapping(configName, getPrimaryKeyName(model)));
      BusinessModelOpenAPISupport.applyCrudOperation(webApiBuilder, model, configName, httpMethod);
      final var webApiModel = webApiBuilder.build();
      context.putModel(WebAPIModel.class, webApiModel.getApi(), webApiModel);
    };
  }

  /**
   * 生成WebAPI的模型处理器
   *
   * @param configName    配置名称
   * @param httpMethod    HTTP方法
   * @param pathBuilder   路径生成函数
   * @param logicFlowName 逻辑流名称
   * @return 模型处理器
   */
  protected Consumer<ModelProjectionPipelineContext<DefaultBusinessModel>> getWebApiModelProcessor(
      String configName, String httpMethod, BiFunction<String, String, String> pathBuilder, String logicFlowName) {
    Predicate<Map<String, Object>> enabledCondition = config -> Objects.equals(config.get("isEnabled"), true);
    return getWebApiModelProcessor(configName, enabledCondition, httpMethod, pathBuilder, logicFlowName);
  }

  /**
   * 带有ID路径参数的路径生成器
   *
   * @param pathPrefix 路径前缀
   * @param idName     ID字段名
   * @return 生成的路径
   */
  protected String buildPathWithId(String pathPrefix, String idName) {
    return pathPrefix + "/{" + idName + "}";
  }

  /**
   * 不带ID路径参数的路径生成器
   *
   * @param pathPrefix    路径前缀
   * @param ignoredIdName ID字段名（忽略）
   * @return 生成的路径
   */
  protected String buildSimplePath(String pathPrefix, String ignoredIdName) {
    return pathPrefix;
  }

  protected BiFunction<String, String, String> getPathBuilderWithId(Function<String, String> afterBuild) {
    return (pathPrefix, idName) -> afterBuild.apply(buildPathWithId(pathPrefix, idName));
  }
}
