package com.ouroboros.businessmodel.projection.authority;

import java.util.Collections;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的删除权限模型
 */
@Priority(73)
public class DeleteMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var isDeleteEnabled = model.getExtraPropDeep(Boolean.class, "apis", "delete", "isEnabled").orElse(false);
    final var hasDataModel = context.getAttribute(Boolean.class, "hasDataModel", false);
    if (!isDeleteEnabled) {
      return;
    }
    if (!hasDataModel) {
      return;
    }
    var authority = createAuthorityForDelete(context);
    context.putModel(Authority.class, authority.getFullName(), authority);
  }

  private Authority createAuthorityForDelete(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var accessControlMap = model.getExtraPropDeep(Map.class, "accessControl", "delete").orElse(Collections.emptyMap());
    return createAuthority(context, ".delete", "删除", accessControlMap, (authority, fullName) -> {
      authority.addApi(buildWebApiPath("DELETE", fullName, "{id}"));
    });
  }
}
