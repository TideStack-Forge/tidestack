package com.ouroboros.businessmodel.projection.authority;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的父级权限模型
 */
@Priority(75)
public class ParentMPS extends AbstractAuthorityMPS {

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    boolean isExistChildAuthority = context.getModel(Authority.class, model.getFullName() + ".search")
        .map(authority -> authority.getUiViews().contains(buildUiViewPath(model.getFullName(), null)))
        .orElse(false);

    final Authority authority;
    authority = createAuthority(context, "", "", Collections.emptyMap(), (a, fullName) -> {
      if (!isExistChildAuthority) {
        a.addUiView(buildUiViewPath(fullName, null));

        List<String> fullNames = Arrays.asList(fullName.split("\\."));
        for (int i = 0; i < fullNames.size(); i++) {
          String name = String.join(".", fullNames.subList(0, i + 1));
          a.addMenuItem(name);
        }
      }
    });
    context.putModel(Authority.class, authority.getFullName(), authority);
  }
}
