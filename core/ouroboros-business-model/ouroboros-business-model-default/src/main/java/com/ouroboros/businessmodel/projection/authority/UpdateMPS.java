package com.ouroboros.businessmodel.projection.authority;

import java.util.Collections;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的更新权限模型
 */
@Priority(72)
public class UpdateMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var isUpdateEnabled = model.getExtraPropDeep(Boolean.class, "apis", "update", "isEnabled").orElse(false);
    final var hasDataModel = context.getAttribute(Boolean.class, "hasDataModel", false);
    if (!isUpdateEnabled) {
      return;
    }
    if (!hasDataModel) {
      return;
    }
    var authority = createAuthorityForUpdate(context);
    context.putModel(Authority.class, authority.getFullName(), authority);
  }

  private Authority createAuthorityForUpdate(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var accessControlMap = model.getExtraPropDeep(Map.class, "accessControl", "update").orElse(Collections.emptyMap());
    return createAuthority(context, ".edit", "编辑", accessControlMap, (authority, fullName) -> {
      authority.addApi(buildWebApiPath("PUT", fullName, "{id}"));
      authority.addApi(buildWebApiPath("PATCH", fullName, "{id}"));
      authority.addUiView(buildUiViewPath(fullName, "edit"));
    });
  }
}
