package com.ouroboros.businessmodel.projection.authority;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的批量删除权限模型
 */
@Priority(71)
public class BatchDeleteMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var importEnabled = model.getExtraPropDeep(Boolean.class, "features", "isEnabledBatchDelete").orElse(false);
    if (!importEnabled) {
      return;
    }
    final var authority = createBatchDeleteAuthority(context);
    context.putModel(Authority.class, authority.getFullName(), authority);
  }

  private Authority createBatchDeleteAuthority(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    return createAuthority(context, ".batchDelete", "批量删除", null, (authority, fullName) -> {
      authority.addApi(buildWebApiPath("POST", fullName, "batch-delete"));
    });
  }
}
