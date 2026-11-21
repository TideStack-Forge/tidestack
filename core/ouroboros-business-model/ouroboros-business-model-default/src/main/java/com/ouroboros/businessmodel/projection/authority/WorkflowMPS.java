package com.ouroboros.businessmodel.projection.authority;

import java.util.Collections;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型工作流包装面的权限模型。
 */
@Priority(70)
public class WorkflowMPS extends AbstractAuthorityMPS {

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getSourceModel().getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false)) {
      return;
    }
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }

    var authority = createAuthority(context, ".workflow", "流程处理", Collections.emptyMap(), (a, fullName) -> {
      a.addApi(buildWebApiPath("POST", fullName, "{id}/start-process"));
      a.addApi(buildWebApiPath("GET", fullName, "{id}/process/instance/{instanceId}/task/{taskId}/init-form"));
      a.addApi(buildWebApiPath("POST", fullName, "{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}"));
      a.addUiView(buildUiViewPath(fullName, "{id}/approval"));
      a.addUiView(buildUiViewPath(fullName, "{id}/process-detail"));
    });
    context.putModel(Authority.class, authority.getFullName(), authority);
  }
}
