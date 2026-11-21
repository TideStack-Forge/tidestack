package com.ouroboros.webapi;

import java.util.Collection;

public interface WebAPIRouteRegistry {
  void register(Collection<WebAPIModel> webAPIModels);
}
