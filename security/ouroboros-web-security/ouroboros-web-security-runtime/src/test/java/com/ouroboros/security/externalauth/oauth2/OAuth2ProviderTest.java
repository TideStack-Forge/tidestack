package com.ouroboros.security.externalauth.oauth2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Either;

import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;

@ExtendWith(MockitoExtension.class)
class OAuth2ProviderTest {

  @Mock
  private OAuth2HttpClient httpClient;

  @Mock
  private JwksKeyResolver jwksKeyResolver;

  @Test
  void callbackAndDirectExchangeClassifyRemoteRejectionWithoutExposingItsBody() {
    var spec = new OAuth2ProviderSpec();
    spec.setKey("feishu-main"); spec.setClientId("client"); spec.setTokenUri("https://idp.example.com/token");
    var provider = new OAuth2Provider(spec, "secret", httpClient, jwksKeyResolver, "https://platform.example.com");
    when(httpClient.exchangeCode(eq(spec.getTokenUri()), eq("replayed"), eq(null), eq(null), eq("client"), eq("secret")))
        .thenThrow(new org.springframework.web.client.RestClientException("upstream body contains sensitive-material"));
    var callback = CallbackParams.fromRedirectCallback("state", Map.of("code", List.of("replayed")), Map.of(), "trace");
    var result = provider.handleCallback(callback);
    assertTrue(result.isLeft());
    assertEquals(AuthError.providerUnavailable("failed").getCode(), result.getLeft().getCode());
    org.junit.jupiter.api.Assertions.assertFalse(result.getLeft().getMessage().contains("sensitive-material"));
    var direct = provider.handleExchange(com.ouroboros.security.externalauth.ExchangeParams.ofCode("replayed", null, null, "trace"));
    assertTrue(direct.isLeft());
    org.junit.jupiter.api.Assertions.assertFalse(direct.getLeft().getMessage().contains("sensitive-material"));
  }

  @Test
  void shouldGenerateRfcCompliantCodeVerifierWhenPkceEnabled() {
    OAuth2ProviderSpec spec = new OAuth2ProviderSpec();
    spec.setKey("oidc");
    spec.setAuthorizationUri("https://idp.example.com/oauth2/authorize");
    spec.setClientId("client-1");
    spec.setPkceEnabled(true);
    spec.setPkceMethod("S256");

    OAuth2Provider provider = new OAuth2Provider(
        spec,
        "secret-1",
        null,
        null,
        "https://shell.example.com"
    );

    AuthorizeUrlResult result = provider.buildAuthorizeUrl("/");

    String codeVerifier = (String) result.getCacheEntry().get("codeVerifier");
    assertNotNull(codeVerifier);
    assertTrue(codeVerifier.length() >= 43, "PKCE code_verifier must be at least 43 characters");
    assertTrue(codeVerifier.length() <= 128, "PKCE code_verifier must be at most 128 characters");
    assertTrue(codeVerifier.matches("^[A-Za-z0-9\\-._~]+$"), "PKCE code_verifier must use unreserved URI characters");
    assertTrue(result.getAuthorizeUrl().contains("code_challenge="));
    assertTrue(result.getAuthorizeUrl().contains("code_challenge_method=S256"));
  }

  @Test
  void shouldBindOidcNonceAndConnectionCallbackToTheAuthorizeRequest() {
    OAuth2ProviderSpec spec = new OAuth2ProviderSpec();
    spec.setKey("feishu-main");
    spec.setAuthorizationUri("https://idp.example.com/oauth2/authorize");
    spec.setClientId("client-1");
    spec.setConnectionScoped(true);

    OAuth2Provider provider = new OAuth2Provider(
        spec,
        "secret-1",
        null,
        null,
        "https://shell.example.com"
    );

    AuthorizeUrlResult result = provider.buildAuthorizeUrl("/");

    assertNotNull(result.getCacheEntry().get("nonce"));
    assertTrue(result.getAuthorizeUrl().contains("nonce="));
    assertTrue(result.getAuthorizeUrl().contains(
        "redirect_uri=https%3A%2F%2Fshell.example.com%2Fapi%2Fauth%2Foauth2%2Ffeishu-main%2Fcallback"));
    assertTrue("feishu-main".equals(result.getCacheEntry().get("connectionId")));
  }

  @Test
  void shouldRejectInvalidIdTokenWithoutFallingBackToUserInfo() {
    OAuth2ProviderSpec spec = new OAuth2ProviderSpec();
    spec.setKey("oidc");
    spec.setAuthorizationUri("https://idp.example.com/oauth2/authorize");
    spec.setTokenUri("https://idp.example.com/oauth2/token");
    spec.setClientId("client-1");
    spec.setIssuer("https://idp.example.com");
    spec.setJwksUri("https://idp.example.com/oauth2/jwks");
    spec.setUserInfoUri("https://idp.example.com/oauth2/userinfo");

    OAuth2Provider provider = new OAuth2Provider(
        spec, "secret-1", httpClient, jwksKeyResolver, "https://shell.example.com");
    when(httpClient.exchangeCode(
        eq(spec.getTokenUri()), eq("code-1"), anyString(), eq(null), eq(spec.getClientId()), eq("secret-1")))
        .thenReturn(Map.of("id_token", "invalid-token", "access_token", "access-token"));
    AuthError idTokenError = AuthError.invalidCredentials("id_token signature invalid");
    when(jwksKeyResolver.verifyAndParse(
        eq("invalid-token"), eq(spec.getJwksUri()), eq(spec.getClientId()), eq(spec.getIssuer())))
        .thenReturn(Either.left(idTokenError));

    Either<AuthError, ?> result = provider.handleCallback(CallbackParams.fromRedirectCallback(
        "state-1", Map.of("code", List.of("code-1")),
        Map.of("redirectUri", "https://shell.example.com/callback"), "cid-1"));

    assertTrue(result.isLeft());
    assertTrue(result.getLeft() == idTokenError);
    verify(httpClient, never()).fetchUserInfo(anyString(), anyString());
  }

  @Test
  void shouldRejectIdTokenNonceMismatchWithoutFallingBackToUserInfo() {
    OAuth2ProviderSpec spec = new OAuth2ProviderSpec();
    spec.setKey("oidc");
    spec.setAuthorizationUri("https://idp.example.com/oauth2/authorize");
    spec.setTokenUri("https://idp.example.com/oauth2/token");
    spec.setClientId("client-1");
    spec.setIssuer("https://idp.example.com");
    spec.setJwksUri("https://idp.example.com/oauth2/jwks");
    spec.setUserInfoUri("https://idp.example.com/oauth2/userinfo");

    OAuth2Provider provider = new OAuth2Provider(
        spec, "secret-1", httpClient, jwksKeyResolver, "https://shell.example.com");
    when(httpClient.exchangeCode(
        eq(spec.getTokenUri()), eq("code-1"), anyString(), eq(null), eq(spec.getClientId()), eq("secret-1")))
        .thenReturn(Map.of("id_token", "valid-token", "access_token", "access-token"));
    Claims claims = mock(Claims.class);
    when(claims.get("nonce")).thenReturn("unexpected-nonce");
    when(jwksKeyResolver.verifyAndParse(
        eq("valid-token"), eq(spec.getJwksUri()), eq(spec.getClientId()), eq(spec.getIssuer())))
        .thenReturn(Either.right(claims));

    Either<AuthError, ?> result = provider.handleCallback(CallbackParams.fromRedirectCallback(
        "state-1", Map.of("code", List.of("code-1")), Map.of(
            "redirectUri", "https://shell.example.com/callback", "nonce", "expected-nonce"), "cid-1"));

    assertTrue(result.isLeft());
    assertTrue("id_token nonce mismatch".equals(result.getLeft().getMessage()));
    verify(httpClient, never()).fetchUserInfo(anyString(), anyString());
  }

  @Test
  void shouldBuildIdentityFromConfiguredUserInfoEnvelope() {
    OAuth2ProviderSpec spec = new OAuth2ProviderSpec();
    spec.setKey("feishu-main");
    spec.setAuthorizationUri("https://accounts.feishu.cn/open-apis/authen/v1/authorize");
    spec.setTokenUri("https://accounts.feishu.cn/oauth/v3/token");
    spec.setUserInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info");
    spec.setUserInfoDataField("data");
    spec.setExternalIdField("open_id");
    spec.setClientId("cli_test");

    OAuth2Provider provider = new OAuth2Provider(
        spec, "secret-1", httpClient, jwksKeyResolver, "https://shell.example.com");
    when(httpClient.exchangeCode(
        eq(spec.getTokenUri()), eq("code-1"), anyString(), eq(null), eq(spec.getClientId()), eq("secret-1")))
        .thenReturn(Map.of("access_token", "user-access-token"));
    when(httpClient.fetchUserInfo(spec.getUserInfoUri(), "user-access-token"))
        .thenReturn(Map.of(
            "code", 0,
            "msg", "success",
            "data", Map.of(
                "open_id", "ou_123",
                "name", "Alice",
                "email", "alice@example.com")));

    var result = provider.handleCallback(CallbackParams.fromRedirectCallback(
        "state-1", Map.of("code", List.of("code-1")),
        Map.of("redirectUri", "https://shell.example.com/callback"), "cid-1"));

    assertTrue(result.isRight());
    assertEquals("feishu-main", result.get().getProviderKey());
    assertEquals("ou_123", result.get().getExternalId());
    assertEquals("Alice", result.get().getDisplayName());
    assertEquals("alice@example.com", result.get().getEmail());
    assertEquals("ou_123", result.get().getAttributes().get("open_id"));
  }

  @Test
  void shouldRejectNonTextStableExternalIds() {
    OAuth2ProviderSpec spec = new OAuth2ProviderSpec();
    spec.setKey("feishu-main");
    spec.setTokenUri("https://accounts.feishu.cn/oauth/v3/token");
    spec.setUserInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info");
    spec.setUserInfoDataField("data");
    spec.setExternalIdField("open_id");
    spec.setClientId("cli_test");

    OAuth2Provider provider = new OAuth2Provider(
        spec, "secret-1", httpClient, jwksKeyResolver, "https://shell.example.com");
    when(httpClient.exchangeCode(
        eq(spec.getTokenUri()), eq("code-1"), anyString(), eq(null), eq(spec.getClientId()), eq("secret-1")))
        .thenReturn(Map.of("access_token", "user-access-token"));
    when(httpClient.fetchUserInfo(spec.getUserInfoUri(), "user-access-token"))
        .thenReturn(
            Map.of("data", Map.of("open_id", 123)),
            Map.of("data", Map.of("open_id", Map.of("value", "ou_123"))),
            Map.of("data", Map.of("open_id", List.of("ou_123"))));

    for (int attempt = 0; attempt < 3; attempt++) {
      var result = provider.handleCallback(CallbackParams.fromRedirectCallback(
          "state-1", Map.of("code", List.of("code-1")),
          Map.of("redirectUri", "https://shell.example.com/callback"), "cid-1"));

      assertTrue(result.isLeft());
      assertEquals("UserInfo response missing stable external ID", result.getLeft().getMessage());
    }
  }
}
