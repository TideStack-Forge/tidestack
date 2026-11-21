package com.ouroboros.dev.runtime.apisix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ApisixGatewayRealSmokeSupportTest {

  @Test
  void shouldIgnoreDeleteFailuresWhenApisixIsUnavailable() {
    assertDoesNotThrow(() -> ApisixGatewayRealSmokeSupport.deleteRoute(
        ApisixGatewayRealSmokeSupport.restTemplate(),
        "http://127.0.0.1:1",
        "smoke-admin-key",
        "rtg-smoke-docker-test-app-crm-dev"
    ));
  }
}
