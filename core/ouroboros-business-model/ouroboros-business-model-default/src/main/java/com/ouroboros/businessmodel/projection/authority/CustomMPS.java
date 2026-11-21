package com.ouroboros.businessmodel.projection.authority;

import java.util.Collections;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.custom.CustomConfig;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.util.Converter;

/**
 * 生成业务模型的自定义功能的权限模型
 */
@Priority(75)
public class CustomMPS extends AbstractAuthorityMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    context.getSourceModel().getCustomConfigs().forEach(config -> addCustomAuthority(context, config));
  }

  private void addCustomAuthority(ModelProjectionPipelineContext<DefaultBusinessModel> context, CustomConfig config) {
    var model = context.getSourceModel();
    final var name = config.getName();
    final var title = config.getTitle();
    if (name.isEmpty()) {
      return;
    }
    final var isUiEnabled = Converter.toBoolean(config.getUi().getIsEnabled());
    final var isApiEnabled = Converter.toBoolean(config.getApi().getIsEnabled());
    if (!isUiEnabled && !isApiEnabled) {
      return;
    }
    String executionName = config.getApi().getIsEnabled() && config.getApi().isActionReference()
        ? config.getApi().getActionName(name) : name;
    String authorityName = config.getApi().getIsEnabled() && config.getApi().isActionReference()
        ? model.getBehavior().executionAuthorityName(executionName) : model.getFullName() + "." + name;
    var authorityItem = context.getModel(Authority.class, authorityName)
        .orElseGet(() -> createAuthority(context, authorityName.substring(model.getFullName().length()), title, Collections.emptyMap(), (ignored, fullName) -> { }));
    if (authorityItem instanceof BusinessModelAuthority authority) {
      if (isUiEnabled) {
        authority.addUiView(buildUiViewPath(model.getFullName(), name));
      }
      if (isApiEnabled) {
        var httpMethod = config.getApi().getHttpMethod();
        var url = config.getApi().getUrl();
        final String apiUrl;
        if (url.startsWith("/")) {
          apiUrl = httpMethod + " /api" + url;
        } else {
          apiUrl = buildWebApiPath(httpMethod, model.getFullName(), url);
        }
        authority.addApi(apiUrl);
      }
    }
    context.putModel(Authority.class, authorityItem.getFullName(), authorityItem);
  }

}
