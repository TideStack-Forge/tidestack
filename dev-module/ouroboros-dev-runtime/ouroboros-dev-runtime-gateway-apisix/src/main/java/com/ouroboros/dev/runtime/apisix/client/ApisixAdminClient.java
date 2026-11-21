package com.ouroboros.dev.runtime.apisix.client;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;

public interface ApisixAdminClient {
  Try<Void> upsertRoute(String routeId, ApisixRoutePayload routePayload);

  Try<Void> deleteRoute(String routeId);
}
