package com.ouroboros.rpc.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class StaticAccessTokenRpcAuthCredentialTest {

  @Test
  void applyToRequest_keepsDefaultAuthorizationPlacementSemantics() {
    StaticAccessTokenRPCAuthCredential credential = credential("token-1", key -> null, null);

    HttpRPCRequest request = new HttpRPCRequest();
    Try<HttpRPCRequest> result = credential.applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess());
    assertEquals("Bearer token-1", request.getHeaders().get("Authorization"));
  }

  @Test
  void applyToRequest_keepsCustomPlacementSemantics() {
    Map<String, String> placement = new HashMap<>();
    placement.put("in", "header");
    placement.put("name", "X-Auth");
    placement.put("value", "Token ${accessToken}");
    StaticAccessTokenRPCAuthCredential credential = credential("token-1", key -> null, placement);

    HttpRPCRequest request = new HttpRPCRequest();
    Try<HttpRPCRequest> result = credential.applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess());
    assertEquals("Token token-1", request.getHeaders().get("X-Auth"));
  }

  @Test
  void applyToRequest_failsWhenLiteralStaticAccessTokenIsBlank() {
    StaticAccessTokenRPCAuthCredential credential = credential("   ", key -> null, null);

    AuthenticateException exception = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));

    assertAll(
        () -> assertTrue(exception.getMessage().contains("testStaticAccessTokenCredential")),
        () -> assertTrue(exception.getMessage().contains("静态访问令牌")),
        () -> assertTrue(exception.getMessage().contains("为空"))
    );
  }

  @Test
  void applyToRequest_failsWhenStaticAccessTokenConfigKeyIsBlank() {
    StaticAccessTokenRPCAuthCredential credential = credential("#{}", key -> "token-from-empty-key", null);

    AuthenticateException exception = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));

    assertAll(
        () -> assertTrue(exception.getMessage().contains("testStaticAccessTokenCredential")),
        () -> assertTrue(exception.getMessage().contains("配置键")),
        () -> assertTrue(exception.getMessage().contains("为空或无效"))
    );
  }

  @Test
  void applyToRequest_failsWhenStaticAccessTokenResolvedFromConfigIsBlank() {
    StaticAccessTokenRPCAuthCredential credential = credential("#{config.key}", key -> "   ", null);

    AuthenticateException exception = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));

    assertAll(
        () -> assertTrue(exception.getMessage().contains("testStaticAccessTokenCredential")),
        () -> assertTrue(exception.getMessage().contains("config.key")),
        () -> assertTrue(exception.getMessage().contains("静态访问令牌")),
        () -> assertTrue(exception.getMessage().contains("为空"))
    );
  }

  @Test
  void applyToRequest_failsWithContextWhenPlacementExpressionIsMalformed() {
    Map<String, String> placement = new HashMap<>();
    placement.put("in", "header");
    placement.put("name", "X-Auth");
    placement.put("value", "Bearer ${accessToken + }");
    StaticAccessTokenRPCAuthCredential credential = credential("token-1", key -> null, placement);

    AuthenticateException exception = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));

    assertAll(
        () -> assertTrue(exception.getMessage().contains("testStaticAccessTokenCredential")),
        () -> assertTrue(exception.getMessage().contains("X-Auth")),
        () -> assertTrue(exception.getMessage().contains("HEADER")),
        () -> assertNotNull(exception.getCause()),
        () -> assertTrue(exception.getCause().getMessage().contains("计算表达式失败"))
    );
  }

  private AuthenticateException assertAuthenticateFailure(Try<HttpRPCRequest> result) {
    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof AuthenticateException);
    return (AuthenticateException) result.getCause();
  }

  private StaticAccessTokenRPCAuthCredential credential(
      String staticAccessToken,
      Function<String, String> configGetter,
      Map<String, String> customPlacement
  ) {
    return new StaticAccessTokenRPCAuthCredential(metadata(staticAccessToken, customPlacement), configGetter);
  }

  private RPCCredentialMetadata metadata(String staticAccessToken, Map<String, String> customPlacement) {
    RPCCredentialMetadata metadata = new RPCCredentialMetadata();
    metadata.setName("testStaticAccessTokenCredential");
    metadata.setType("AccessToken");
    metadata.setEnabled(true);

    Map<String, Object> accessToken = new HashMap<>();
    accessToken.put("mode", "static");
    accessToken.put("staticAccessToken", staticAccessToken);
    if (Objects.nonNull(customPlacement)) {
      accessToken.put("tokenPlacement", "custom");
      accessToken.put("customPlacement", Collections.singletonList(customPlacement));
    }

    metadata.setExtraProps(Collections.singletonMap("accessToken", accessToken));
    return metadata;
  }
}
