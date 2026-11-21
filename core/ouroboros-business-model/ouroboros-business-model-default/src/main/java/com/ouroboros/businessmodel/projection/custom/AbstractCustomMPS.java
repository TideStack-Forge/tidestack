package com.ouroboros.businessmodel.projection.custom;

import java.util.Map;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.BusinessModelUtil;

public abstract class AbstractCustomMPS extends AbstractDefaultBusinessModelProcessStep {
  @Override
  public final void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    model.getCustomConfigs().forEach(config -> doProcess(config, context));
  }

  protected abstract void doProcess(CustomConfig config, ModelProjectionPipelineContext<DefaultBusinessModel> context);

  protected <T> T getMapValue(Map<?, ?> config, Class<T> clazz, String key) {
    return getMapValue(config, clazz, key, null);
  }

  protected <T> T getMapValue(Map<?, ?> config, Class<T> clazz, String key, T defaultValue) {
    var value = config.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return defaultValue;
  }

  protected String buildCustomApiUrl(CustomConfig config, DefaultBusinessModel model) {
    var prefix = BusinessModelUtil.buildKebabCasePath(model);
    var url = config.getApi().getUrl();
    return url.startsWith("/") ? url : (prefix + "/" + url);
  }
}
