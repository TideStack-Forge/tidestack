package com.ouroboros.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.cas.CasProvider;
import com.ouroboros.security.externalauth.config.ExternalAuthProperties;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.web.WebApiResult;

import io.vavr.control.Either;

@ExtendWith(MockitoExtension.class)
class CasAuthControllerTest {

  @Mock
  private ExternalAuthProviderFactory providerFactory;

  @Mock
  private ExternalAuthService externalAuthService;

  @Mock
  private CacheManager cacheManager;

  @Mock
  private RedirectAuthProvider redirectProvider;

  @Mock
  private CasProvider casProvider;

  @Mock
  private HttpServletResponse response;

  private CasAuthController controller;

  @BeforeEach
  void setUp() {
    controller = new CasAuthController();
    org.mockito.Mockito.lenient().when(redirectProvider.getSpec())
        .thenReturn(new com.ouroboros.security.externalauth.config.CasProviderSpec());

    ExternalAuthProperties properties = new ExternalAuthProperties();
    properties.setPublicBaseUrl("https://public.example.com");

    ReflectionTestUtils.setField(controller, "providerFactory", providerFactory);
    ReflectionTestUtils.setField(controller, "externalAuthService", externalAuthService);
    ReflectionTestUtils.setField(controller, "externalAuthProperties", (ExternalAuthConfiguration) () -> properties);
    ReflectionTestUtils.setField(controller, "cacheManager", cacheManager);
  }

  @Test
  void shouldRejectProtocolRelativeReturnUrl() {
    Boolean valid = ReflectionTestUtils.invokeMethod(controller, "isValidReturnUrl", "//evil.com");
    assertFalse(valid.booleanValue());
  }

  @Test
  void shouldFallbackToRootWhenLoginReturnUrlIsInvalid() throws Exception {
    when(providerFactory.getRedirectProvider("cas", "cas")).thenReturn(Optional.of(redirectProvider));
    when(redirectProvider.buildAuthorizeUrl("/")).thenReturn(
        AuthorizeUrlResult.of("https://cas.example.com/login", "state-1", Collections.singletonMap("serviceUrl", "svc"))
    );

    controller.login("cas", "//evil.com", response);

    verify(redirectProvider).buildAuthorizeUrl("/");
    verify(cacheManager).put(eq("ouroboros:auth:cas-state"), eq("state-1"), anyMap(), eq(300_000L));
    verify(response).sendRedirect("https://cas.example.com/login");
    verify(response, never()).sendError(anyInt(), anyString());
  }

  @Test
  void shouldRedirectToLoginErrorWhenProviderMissingInCallback() throws Exception {
    when(providerFactory.getRedirectProvider("cas", "cas")).thenReturn(Optional.empty());

    controller.callback("cas", "state-1", "ticket-1", response);

    verify(response).sendRedirect("https://public.example.com/login?error=50201");
    verify(response, never()).sendError(anyInt(), anyString());
  }

  @Test
  void shouldRejectCallbackWhenUnifiedChannelConfigurationChanged() throws Exception {
    var profile = new com.ouroboros.security.externalauth.config.CasProviderSpec();
    profile.setConfigurationRevision("new-revision");
    when(redirectProvider.getSpec()).thenReturn(profile);
    when(providerFactory.getRedirectProvider("cas", "cas")).thenReturn(Optional.of(redirectProvider));
    when(cacheManager.getAndEvict("ouroboros:auth:cas-state", "state-1"))
        .thenReturn(Optional.of(Map.of("providerRevision", "old-revision")));

    controller.callback("cas", "state-1", "ticket-1", response);

    verify(redirectProvider, never()).handleCallback(any());
    verify(response).sendRedirect("https://public.example.com/login?error=40102");
  }

  @Test
  void shouldRedirectByErrorCodeWithoutLeakingMessageWhenCallbackFails() throws Exception {
    when(providerFactory.getRedirectProvider("cas", "cas")).thenReturn(Optional.of(redirectProvider));

    Map<String, Object> cachedState = new HashMap<String, Object>();
    cachedState.put("returnUrl", "/dashboard");
    cachedState.put("correlationId", "cid-1");
    when(cacheManager.getAndEvict("ouroboros:auth:cas-state", "state-1")).thenReturn(Optional.of(cachedState));
    when(redirectProvider.handleCallback(any())).thenReturn(
        Either.left(AuthError.providerUnavailable("internal-cas-stack"))
    );

    controller.callback("cas", "state-1", "ticket-1", response);

    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).sendRedirect(redirectCaptor.capture());

    String redirectUrl = redirectCaptor.getValue();
    assertEquals("https://public.example.com/login?error=50201", redirectUrl);
    assertFalse(redirectUrl.contains("internal-cas-stack"));
    verify(response, never()).sendError(anyInt(), anyString());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnCachedFinalResultWhenCasExchangeRetried() {
    when(providerFactory.getRedirectProvider("cas", "cas")).thenReturn(Optional.of(casProvider));
    when(casProvider.consumeFinalResult("ticket-1")).thenReturn(
        Optional.of(CasProvider.CasTicketFinalResult.loggedIn("access-1", "refresh-1"))
    );

    CasAuthController.CasExchangeRequest request = new CasAuthController.CasExchangeRequest();
    request.setTicket("ticket-1");
    WebApiResult<?> result = controller.exchange("cas", request);

    assertEquals(Integer.valueOf(0), result.getCode());
    assertNotNull(result.getData());
    Map<String, Object> data = (Map<String, Object>) result.getData();
    assertEquals("access-1", data.get("accessToken"));
    assertEquals("refresh-1", data.get("refreshToken"));
    verify(casProvider, never()).handleExchange(any());
  }
}
