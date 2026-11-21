package com.ouroboros.businessmodel.projection.authority;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的导出权限模型
 */
@Priority(71)
public class ServerSideExportMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var importEnabled = model.getExtraPropDeep(Boolean.class, "features.isEnabledServerSideExport").orElse(false);
    if (!importEnabled) {
      return;
    }
    final var authority = createImportAuthority(context);
    context.putModel(Authority.class, authority.getFullName(), authority);
  }

  private Authority createImportAuthority(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    return createAuthority(context, ".serverSideExport", "导出", null, (authority, fullName) -> {
      authority.addApi("GET /api/default-business-model-data-export/" + model.getFullName() + "/export-excel");
    });
  }
}
