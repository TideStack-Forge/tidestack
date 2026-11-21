package com.ouroboros.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ouroboros.security.externalauth.AuthResult;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.config.ExternalAuthProperties;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.cache.CacheManager;

import io.vavr.control.Either;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthControllerTest {

  @Mock
  private ExternalAuthProviderFactory providerFactory;

  @Mock
  private ExternalAuthService externalAuthService;

  @Mock
  private RedirectAuthProvider redirectAuthProvider;

  @Mock
  private CacheManager cacheManager;

  @Mock
  private HttpServletResponse response;

  private OAuth2AuthController controller;

  @BeforeEach
  void setUp() {
    controller = new OAuth2AuthController();
    ExternalAuthProperties properties = new ExternalAuthProperties();
    properties.setPublicBaseUrl("https://public.example.com");

    ReflectionTestUtils.setField(controller, "providerFactory", providerFactory);
    ReflectionTestUtils.setField(controller, "externalAuthService", externalAuthService);
    ReflectionTestUtils.setField(controller, "externalAuthProperties", (ExternalAuthConfiguration) () -> properties);
    ReflectionTestUtils.setField(controller, "cacheManager", cacheManager);
  }

  @Test
  void shouldReturnProviderUnavailableWhenExchangeProviderMissing() {
    OAuth2AuthController.OAuth2ExchangeRequest request = new OAuth2AuthController.OAuth2ExchangeRequest();
    request.setCode("code-1");
    request.setRedirectUri("/callback");

    when(providerFactory.getRedirectProvider("oidc", "oauth2")).thenReturn(Optional.empty());

    WebApiResult<?> result = controller.exchange("oidc", request);

    assertEquals(Integer.valueOf(50201), result.getCode());
  }

  @Test
  void shouldReturnBindingTokenWhenOAuth2ExchangeNeedsBinding() {
    OAuth2AuthController.OAuth2ExchangeRequest request = new OAuth2AuthController.OAuth2ExchangeRequest();
    request.setCode("code-2");
    request.setRedirectUri("/callback");

    ExternalIdentity identity = ExternalIdentity.of(
        "oidc",
        "external-oidc-1",
        "OIDC User",
        "oidc@example.com",
        Collections.emptyMap()
    );

    OAuth2ProviderSpec profile = new OAuth2ProviderSpec();
    profile.setType("oauth2");
    when(redirectAuthProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getRedirectProvider("oidc", "oauth2"))
        .thenReturn(Optional.of(redirectAuthProvider));
    when(redirectAuthProvider.handleExchange(any())).thenReturn(Either.right(identity));
    when(externalAuthService.processExternalIdentity(any(), any())).thenReturn(
        Either.right(AuthResult.bindingRequired("binding-token-1"))
    );

    WebApiResult<?> result = controller.exchange("oidc", request);

    assertEquals(Integer.valueOf(40901), result.getCode());
    assertNotNull(result.getData());
  }

  @Test
  void shouldBindConnectionIdToTheOAuthState() throws Exception {
    ProviderSpec profile = new ProviderSpec();
    profile.setType("wecom-bridge");
    profile.setConnectionScoped(true);
    profile.setConnectionProfileRevision("revision-1");
    when(redirectAuthProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getOAuth2FlowProvider("wecom-main"))
        .thenReturn(Optional.of(redirectAuthProvider));
    when(redirectAuthProvider.buildAuthorizeUrl("/work-items")).thenReturn(
        AuthorizeUrlResult.of("https://accounts.example.com/authorize", "state-1", Map.of("nonce", "nonce-1")));

    controller.authorize("wecom-main", "/work-items", response);

    ArgumentCaptor<Map> cacheEntry = ArgumentCaptor.forClass(Map.class);
    verify(cacheManager).put(eq("ouroboros:auth:oauth2-state"), eq("state-1"), cacheEntry.capture(), eq(300_000L));
    assertEquals("wecom-main", cacheEntry.getValue().get("providerKey"));
    assertEquals("wecom-main", cacheEntry.getValue().get("connectionId"));
    assertEquals("revision-1", cacheEntry.getValue().get("connectionProfileRevision"));
    verify(response).sendRedirect("https://accounts.example.com/authorize");
  }

  @Test
  void shouldRejectCallbackWhoseStateWasIssuedForAnotherConnection() throws Exception {
    when(cacheManager.getAndEvict("ouroboros:auth:oauth2-state", "state-1"))
        .thenReturn(Optional.of(Map.of("providerKey", "other-connection", "returnUrl", "/")));

    controller.callback("feishu-main", callbackParameters("state-1", "code", "code-1"), response);

    verifyNoInteractions(providerFactory);
    verify(response).sendRedirect("https://public.example.com/login?error=40102");
  }

  @Test
  void shouldRejectDirectExchangeForConnectionScopedOAuth() {
    ProviderSpec profile = new ProviderSpec();
    profile.setType("dingtalk-bridge");
    profile.setConnectionScoped(true);
    when(redirectAuthProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getRedirectProvider("feishu-main", "oauth2"))
        .thenReturn(Optional.of(redirectAuthProvider));
    OAuth2AuthController.OAuth2ExchangeRequest request = new OAuth2AuthController.OAuth2ExchangeRequest();
    request.setCode("code-1");

    WebApiResult<?> result = controller.exchange("feishu-main", request);

    assertEquals(Integer.valueOf(40101), result.getCode());
    verify(redirectAuthProvider, never()).handleExchange(any());
  }

  @Test
  void shouldRejectCallbackWhenConnectionProfileRevisionChanged() throws Exception {
    ProviderSpec profile = new ProviderSpec();
    profile.setType("wecom-bridge");
    profile.setConnectionScoped(true);
    profile.setConnectionProfileRevision("revision-2");
    when(cacheManager.getAndEvict("ouroboros:auth:oauth2-state", "state-1"))
        .thenReturn(Optional.of(Map.of(
            "providerKey", "feishu-main",
            "connectionId", "feishu-main",
            "connectionProfileRevision", "revision-1",
            "returnUrl", "/")));
    when(redirectAuthProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getOAuth2FlowProvider("feishu-main"))
        .thenReturn(Optional.of(redirectAuthProvider));

    controller.callback("feishu-main", callbackParameters("state-1", "code", "code-1"), response);

    verify(redirectAuthProvider, never()).handleCallback(any());
    verify(response).sendRedirect("https://public.example.com/login?error=40102");
  }

  @Test
  void shouldRejectCallbackWhenUnifiedChannelConfigurationChanged() throws Exception {
    var profile = new OAuth2ProviderSpec();
    profile.setConfigurationRevision("new-revision");
    when(redirectAuthProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getOAuth2FlowProvider("corp")).thenReturn(Optional.of(redirectAuthProvider));
    when(cacheManager.getAndEvict("ouroboros:auth:oauth2-state", "state-1"))
        .thenReturn(Optional.of(Map.of("providerKey", "corp", "providerRevision", "old-revision")));

    controller.callback("corp", callbackParameters("state-1", "code", "code-1"), response);

    verify(redirectAuthProvider, never()).handleCallback(any());
    verify(response).sendRedirect("https://public.example.com/login?error=40102");
  }

  @Test
  void shouldRejectStaticNonOAuthRedirectProviderOnOAuth2Route() throws Exception {
    when(providerFactory.getOAuth2FlowProvider("cas-main")).thenReturn(Optional.empty());

    controller.authorize("cas-main", "/", response);

    verify(response).sendRedirect("https://public.example.com/login?error=50201");
    verify(redirectAuthProvider, never()).buildAuthorizeUrl(any());
  }

  @Test
  void shouldPassRawCallbackParametersToProvider() throws Exception {
    ProviderSpec profile = new ProviderSpec();
    profile.setType("dingtalk-enterprise");
    profile.setConnectionScoped(true);
    profile.setConnectionProfileRevision("revision-1");
    when(cacheManager.getAndEvict("ouroboros:auth:oauth2-state", "state-1"))
        .thenReturn(Optional.of(Map.of(
            "providerKey", "dingtalk-main",
            "connectionId", "dingtalk-main",
            "connectionProfileRevision", "revision-1",
            "returnUrl", "/")));
    when(redirectAuthProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getOAuth2FlowProvider("dingtalk-main"))
        .thenReturn(Optional.of(redirectAuthProvider));
    when(redirectAuthProvider.handleCallback(any()))
        .thenReturn(Either.left(com.ouroboros.security.externalauth.AuthError
            .invalidCredentials("test stop")));

    controller.callback(
        "dingtalk-main", callbackParameters("state-1", "authCode", "ding-code"), response);

    ArgumentCaptor<CallbackParams> callback = ArgumentCaptor.forClass(CallbackParams.class);
    verify(redirectAuthProvider).handleCallback(callback.capture());
    // 平台不再假定通用参数名 code：钉钉回调只带 authCode，provider 拿到的是原始参数表。
    assertNull(callback.getValue().getSingleCallbackParameter("code"));
    assertEquals("ding-code", callback.getValue().getSingleCallbackParameter("authCode"));
  }

  @Test
  void shouldRejectCallbackWithoutExactlyOneState() throws Exception {
    controller.callback("dingtalk-main", new LinkedMultiValueMap<>(), response);

    verifyNoInteractions(cacheManager, providerFactory, redirectAuthProvider);
    verify(response).sendRedirect("https://public.example.com/login?error=40102");
  }

  private MultiValueMap<String, String> callbackParameters(
      String state, String credentialName, String credentialValue) {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("state", state);
    parameters.add(credentialName, credentialValue);
    return parameters;
  }
}
