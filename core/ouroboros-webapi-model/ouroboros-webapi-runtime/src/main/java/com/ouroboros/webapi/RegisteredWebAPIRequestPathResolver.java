package com.ouroboros.webapi;

import com.ouroboros.webapi.util.WebAPIUtils;

public class RegisteredWebAPIRequestPathResolver implements WebAPIRequestPathResolver {
  @Override
  public String resolve(WebAPIModel model) {
    return model.getScope() == null ? WebAPIUtils.pathNormalizer(model.getPath())
        : WebAPIUtils.pathNormalizer(model.getPath(), model.getScope());
  }
}
