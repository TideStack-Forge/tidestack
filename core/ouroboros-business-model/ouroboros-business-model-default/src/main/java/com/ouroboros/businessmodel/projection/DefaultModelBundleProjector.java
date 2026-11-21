package com.ouroboros.businessmodel.projection;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.SPI;

/**
 * 默认模型投射器
 */
public class DefaultModelBundleProjector implements ModelBundleProjector<DefaultBusinessModel> {
  private final List<ModelProcessStep<DefaultBusinessModel>> processors;

  @SuppressWarnings("unchecked")
  public DefaultModelBundleProjector() {
    processors = SPI.getCachedSortedServiceStream(ModelProcessStep.class, DefaultBusinessModel.class)
        .map(p -> (ModelProcessStep<DefaultBusinessModel>) p)
        .filter((p) -> p.support(this))
        .collect(Collectors.toList());
  }

  @Override
  public ModelBundle project(DefaultBusinessModel model) {
    var context = new ModelProjectionPipelineContext<>(model);
    processors.forEach(p -> p.process(context));
    if (!context.getErrors().isEmpty()) {
      var failure = new BusinessException("业务模型投影失败：" + model.getFullName() + ": "
          + context.getErrors().stream().map(Throwable::getMessage).collect(Collectors.joining("; ")),
          context.getErrors().get(0));
      context.getErrors().stream().skip(1).forEach(failure::addSuppressed);
      throw failure;
    }
    return context.getBundle();
  }
}
