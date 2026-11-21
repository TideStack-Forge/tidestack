package com.ouroboros.businessmodel.projection.authority;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

/**
 * 生成业务模型的搜索权限
 */
@Priority(70)
public class SearchMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var isSearchable = model.getExtraPropDeep(Boolean.class, "apis", "search", "isEnabled").orElse(false);
    final var hasDataModel = context.getAttribute(Boolean.class, "hasDataModel", false);
    boolean hasFeatureRead = java.util.stream.Stream.of("searchDeleted", "searchArchive", "searchHistory")
        .anyMatch(name -> model.getBehavior().getActions().containsKey(name));
    if (!isSearchable && !hasFeatureRead) {
      return;
    }
    if (!hasDataModel) {
      return;
    }
    Authority authority = createSearchAuthority(context, isSearchable);
    context.putModel(Authority.class, authority.getFullName(), authority);
  }

  private Authority createSearchAuthority(ModelProjectionPipelineContext<DefaultBusinessModel> context, boolean exposeSearch) {
    final var model = context.getSourceModel();
    final var accessControlMap = model.getExtraPropDeep(Map.class, "accessControl", "search").orElse(Collections.emptyMap());
    return createAuthority(context, ".search", "查询", accessControlMap, (authority, fullName) -> {
      if (!exposeSearch) { return; }
      authority.addApi(buildWebApiPath("GET", fullName, null));
      authority.addApi(buildWebApiPath("GET", fullName, "{id}"));
      authority.addUiView(buildUiViewPath(fullName, null));

      List<String> fullNames = Arrays.asList(fullName.split("\\."));
      for (int i = 0; i < fullNames.size(); i++) {
        String name = String.join(".", fullNames.subList(0, i + 1));
        authority.addMenuItem(name);
      }
    });
  }
}
