package com.ouroboros.businessmodel.projection.authority;

import java.util.Collections;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的添加权限模型
 */
@Priority(71)
public class AddMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var isAddable = model.getExtraPropDeep(Boolean.class, "apis", "add", "isEnabled").orElse(false);
    final var hasDataModel = context.getAttribute(Boolean.class, "hasDataModel", false);
    if (!isAddable) {
      return;
    }
    if (!hasDataModel) {
      return;
    }
    final var authority = createAuthorityForAdd(context);
    context.putModel(Authority.class, authority.getFullName(), authority);
  }

  private Authority createAuthorityForAdd(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var accessControlMap = model.getExtraPropDeep(Map.class, "accessControl", "add").orElse(Collections.emptyMap());
    return createAuthority(context, ".add", "新建", accessControlMap, (authority, fullName) -> {
      authority.addApi(buildWebApiPath("POST", fullName, null));
      authority.addUiView(buildUiViewPath(fullName, "add"));
    });
  }
}
