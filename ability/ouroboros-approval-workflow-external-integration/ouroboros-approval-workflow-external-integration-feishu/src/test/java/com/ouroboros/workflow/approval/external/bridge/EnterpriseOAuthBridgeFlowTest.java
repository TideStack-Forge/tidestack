package com.ouroboros.workflow.approval.external.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.controller.OAuth2AuthController;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;

class EnterpriseOAuthBridgeFlowTest {

  private static final String STATE_CACHE = "ouroboros:auth:oauth2-state";
  private static final String CONNECTION_ID = "feishu-main";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(EnterpriseOAuthBridgeTestConfiguration.class);

  @Test
  void rejectsCallbackWhenConnectionCredentialChangesDuringAuthorization() {
    contextRunner.run(context -> {
      Map<String, Object> states = installStateCache(context.getBean(CacheManager.class));
      ExternalAuthService externalAuthService = org.mockito.Mockito.mock(ExternalAuthService.class);
      OAuth2AuthController controller = controller(context, externalAuthService);
      MockHttpServletResponse authorizeResponse = new MockHttpServletResponse();

      authorize(controller, authorizeResponse);

      assertThat(authorizeResponse.getRedirectedUrl())
          .startsWith("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
          .contains("client_id=cli_context");
      String state = onlyState(states);
      context.getBean(EnterpriseOAuthBridgeTestConfiguration.EnterpriseOAuthBridgeConnections.class)
          .replace(List.of(connection(CONNECTION_ID, "feishu", "test:feishu-main-rotated")));

      MockHttpServletResponse callbackResponse = new MockHttpServletResponse();
      callback(controller, state, "access_denied", callbackResponse);

      assertThat(callbackResponse.getRedirectedUrl())
          .isEqualTo("https://platform.example.com/login?error=40102");
      verifyNoInteractions(externalAuthService);
    });
  }

  @Test
  void preservesOAuthErrorHandlingWhenConnectionIsUnchanged() {
    contextRunner.run(context -> {
      Map<String, Object> states = installStateCache(context.getBean(CacheManager.class));
      ExternalAuthService externalAuthService = org.mockito.Mockito.mock(ExternalAuthService.class);
      OAuth2AuthController controller = controller(context, externalAuthService);

      authorize(controller, new MockHttpServletResponse());

      MockHttpServletResponse callbackResponse = new MockHttpServletResponse();
      callback(controller, onlyState(states), "access_denied", callbackResponse);

      assertThat(callbackResponse.getRedirectedUrl())
          .isEqualTo("https://platform.example.com/login?error=40101");
      verifyNoInteractions(externalAuthService);
    });
  }

  private Map<String, Object> installStateCache(CacheManager cacheManager) {
    Map<String, Object> states = new HashMap<>();
    doAnswer(invocation -> {
      states.put(invocation.getArgument(1, String.class), invocation.getArgument(2));
      return null;
    }).when(cacheManager).put(eq(STATE_CACHE), anyString(), any(), anyLong());
    when(cacheManager.getAndEvict(eq(STATE_CACHE), anyString())).thenAnswer(invocation ->
        Optional.ofNullable(states.remove(invocation.getArgument(1, String.class))));
    return states;
  }

  private OAuth2AuthController controller(
      org.springframework.context.ApplicationContext context, ExternalAuthService externalAuthService) {
    OAuth2AuthController controller = new OAuth2AuthController();
    ReflectionTestUtils.setField(
        controller, "providerFactory", context.getBean(ExternalAuthProviderFactory.class));
    ReflectionTestUtils.setField(controller, "externalAuthService", externalAuthService);
    ReflectionTestUtils.setField(
        controller, "externalAuthProperties", context.getBean(ExternalAuthConfiguration.class));
    ReflectionTestUtils.setField(controller, "cacheManager", context.getBean(CacheManager.class));
    return controller;
  }

  private void authorize(OAuth2AuthController controller, MockHttpServletResponse response) {
    try {
      controller.authorize(CONNECTION_ID, "/work-items", response);
    } catch (IOException exception) {
      throw new AssertionError("OAuth2 authorization should redirect", exception);
    }
  }

  /** 回调走与生产一致的原始参数表入口（平台不再提供"已归一化 code"的兼容重载）。 */
  private void callback(
      OAuth2AuthController controller, String state, String oauthError, MockHttpServletResponse response) {
    MultiValueMap<String, String> callbackParameters = new LinkedMultiValueMap<>();
    callbackParameters.add("state", state);
    if (oauthError != null) {
      callbackParameters.add("error", oauthError);
    }
    try {
      controller.callback(CONNECTION_ID, callbackParameters, response);
    } catch (IOException exception) {
      throw new AssertionError("OAuth2 callback should redirect", exception);
    }
  }

  private String onlyState(Map<String, Object> states) {
    assertThat(states).hasSize(1);
    return states.keySet().iterator().next();
  }

  private Map<String, Object> connection(String connectionId, String provider, String credentialRef) {
    return Map.of(
        "connectionId", connectionId,
        "provider", provider,
        "credentialRef", credentialRef);
  }
}
