package com.ouroboros.businessmodel.projection.webapi;

import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 更新接口的Web API模型
 */
@Priority(13)
public class UpdateMPS extends AbstractWebAPIMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var putProcessor = getWebApiModelProcessor("update", this::canFullUpdate, "PUT", this::buildPathWithId, "update");
    final var patchProcessor = getWebApiModelProcessor("update", this::canPatchUpdate, "PATCH", this::buildPathWithId, "update");
    putProcessor.accept(context);
    patchProcessor.accept(context);
  }

  private boolean canFullUpdate(Map<String, Object> config) {
    return Objects.equals(config.get("isEnabled"), true);
  }

  private boolean canPatchUpdate(Map<String, Object> config) {
    return Objects.equals(config.get("isEnabled"), true);
  }

}

