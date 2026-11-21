package com.ouroboros.dev.runtime.apisix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;

import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.gateway.host.DefaultStableDomainHostResolver;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

@EnabledIfEnvironmentVariable(named = "APISIX_GATEWAY_REAL_SMOKE", matches = "(?i:true|1|yes)")
class ApisixGatewayRealSmokeTest {

  @Test
  void shouldCreateAndDeleteRouteThroughRealApisixAdminApi() {
    String baseUrl = requiredEnv("APISIX_GATEWAY_BASE_URL");
    String adminKey = requiredEnv("APISIX_GATEWAY_ADMIN_KEY");
    String stableDomainSuffix = envOrDefault("APISIX_GATEWAY_STABLE_DOMAIN_SUFFIX", "develop.example.com");
    String routeIdPrefix = "rtg-smoke-" + System.currentTimeMillis();
    String routeId = routeIdPrefix + "-docker-test-app-crm-dev";
    org.springframework.web.client.RestTemplate restTemplate = ApisixGatewayRealSmokeSupport.restTemplate();

    try {
      new ApplicationContextRunner()
          .withPropertyValues(
              "ouroboros.dev.runtime.gateway.enabled=true",
              "ouroboros.dev.runtime.gateway.provider=apisix",
              "ouroboros.dev.runtime.gateway.stable-domain-host-suffix=" + stableDomainSuffix,
              "ouroboros.dev.runtime.gateway.apisix.base-url=" + baseUrl,
              "ouroboros.dev.runtime.gateway.apisix.admin-key=" + adminKey,
              "ouroboros.dev.runtime.gateway.apisix.route-id-prefix=" + routeIdPrefix
          )
          .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
          .run(context -> {
            DevRuntimeEventPublisher eventPublisher = context.getBean(DevRuntimeEventPublisher.class);
            String host = new DefaultStableDomainHostResolver(stableDomainSuffix).resolve("crm", "dev");

            eventPublisher.publish(runtimeCreatedEvent());

            ResponseEntity<String> routeResponse = restTemplate.exchange(
                routeUrl(baseUrl, routeId),
                HttpMethod.GET,
                ApisixGatewayRealSmokeSupport.requestEntity(adminKey),
                String.class
            );
            assertTrue(routeResponse.getStatusCode().is2xxSuccessful());
            assertTrue(routeResponse.getBody() != null && routeResponse.getBody().contains(host));

            eventPublisher.publish(runtimeDeletedEvent());

            assertFalse(routeExists(restTemplate, baseUrl, adminKey, routeId));
          });
    } finally {
      ApisixGatewayRealSmokeSupport.deleteRoute(restTemplate, baseUrl, adminKey, routeId);
    }
  }

  private static boolean routeExists(org.springframework.web.client.RestTemplate restTemplate, String baseUrl, String adminKey, String routeId) {
    try {
      ResponseEntity<String> response = restTemplate.exchange(
          routeUrl(baseUrl, routeId),
          HttpMethod.GET,
          ApisixGatewayRealSmokeSupport.requestEntity(adminKey),
          String.class
      );
      return response.getStatusCode().is2xxSuccessful();
    } catch (HttpStatusCodeException ex) {
      return false;
    }
  }

  private static String routeUrl(String baseUrl, String routeId) {
    return baseUrl + "/apisix/admin/routes/" + routeId;
  }

  private static String requiredEnv(String key) {
    String value = System.getenv(key);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException("Missing required environment variable: " + key);
    }
    return value.trim();
  }

  private static String envOrDefault(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

  private static DevRuntimeEvent runtimeCreatedEvent() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("runtime-1")
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("http://127.0.0.1:18080")
        .apiEndpoint("http://127.0.0.1:18081")
        .build();
  }

  private static DevRuntimeEvent runtimeDeletedEvent() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_DELETED)
        .runtimeId("runtime-1")
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.STOPPED)
        .build();
  }
}
