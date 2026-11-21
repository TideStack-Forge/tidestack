package com.ouroboros.businessmodel.projection.authority;

import java.util.Collections;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/** Projects enabled dynamic Actions into the existing Authority model. */
@Priority(76)
public class BehaviorActionMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    Authority catalog = createAuthority(context, ".behaviors.inspect", "查看业务行为建模契约", Collections.emptyMap(),
        (authority, fullName) -> authority.addApi("GET /api/business-model-behaviors/" + fullName + "/catalog"));
    context.putModel(Authority.class, catalog.getFullName(), catalog);
    for (ActionDefinition action : model.getActions()) {
      if (action == null || !Boolean.TRUE.equals(action.getIsEnabled()) || action.getName() == null) {
        continue;
      }
      String authorityName = model.getBehavior().executionAuthorityName(action.getName());
      Authority authority = context.getModel(Authority.class, authorityName).orElseGet(() ->
          createAuthority(context, authorityName.substring(model.getFullName().length()), action.getTitle() == null ? action.getName() : action.getTitle(),
              Collections.emptyMap(), (ignored, fullName) -> { }));
      if (authority instanceof BusinessModelAuthority businessAuthority) {
        String base = "/api/business-model-behaviors/" + model.getFullName() + "/actions";
        businessAuthority.addApi("GET " + base);
        businessAuthority.addApi("POST " + base + "/" + action.getName() + "/availability");
        businessAuthority.addApi("POST " + base + "/" + action.getName() + "/invoke");
      }
      context.putModel(Authority.class, authority.getFullName(), authority);
    }
  }
}
