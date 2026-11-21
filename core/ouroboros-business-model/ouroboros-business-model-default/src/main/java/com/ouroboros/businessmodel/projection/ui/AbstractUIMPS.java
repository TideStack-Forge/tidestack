package com.ouroboros.businessmodel.projection.ui;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;

@SuppressWarnings({"unused"})
public abstract class AbstractUIMPS extends AbstractDefaultBusinessModelProcessStep {

  final static String VIEWS_CONFIG_KEY = "views";
  final static String WEBAPIS_CONFIG_KEY = "apis";

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected Map<String, Object> getUiConfig(DefaultBusinessModel model, String uiName) {
    return (Map<String, Object>) model.getExtraProp(Map.class, VIEWS_CONFIG_KEY)
        .map(viewsConfig -> viewsConfig.get(uiName))
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

  /**
   * 生成UI模型的的模型处理器
   *
   * @param configName       配置名称
   * @param enabledCondition 启用条件
   * @param pathBuilder      路径构建器
   * @param uiTitle          UI标题
   * @return 模型处理器
   */
  protected Function<ModelProjectionPipelineContext<DefaultBusinessModel>, Optional<UIModel>> getUiModelProcessor(
      String configName, Predicate<Map<String, Object>> enabledCondition, Function<String, String> pathBuilder, String uiTitle) {
    return context -> {
      final var model = context.getSourceModel();
      final var addUIConfig = getUiConfig(model, configName);
      if (!enabledCondition.test(addUIConfig)) {
        return Optional.empty();
      }
      // 生成UI模型
      final var basePath = BusinessModelUtil.buildKebabCasePath(model);
      final var uiPath = pathBuilder.apply(basePath);
      final var uimodel = new SimpleUIModel();
      uimodel.setTitle(uiTitle);
      uimodel.setPath(uiPath);
      uimodel.setType("amis");
      uimodel.setExtraProp("schemaId", uiPath);
      return Optional.of(uimodel);
    };
  }

  /**
   * 生成UI模型的的模型处理器
   *
   * @param configName  配置名称
   * @param pathBuilder 路径生成函数
   * @param uiTitle     UI标题
   * @return 模型处理器
   */
  protected Function<ModelProjectionPipelineContext<DefaultBusinessModel>, Optional<UIModel>> getUiModelProcessor(
      String configName, Function<String, String> pathBuilder, String uiTitle) {
    Predicate<Map<String, Object>> enabledCondition = config -> true;
    return getUiModelProcessor(configName, enabledCondition, pathBuilder, uiTitle);
  }
}
