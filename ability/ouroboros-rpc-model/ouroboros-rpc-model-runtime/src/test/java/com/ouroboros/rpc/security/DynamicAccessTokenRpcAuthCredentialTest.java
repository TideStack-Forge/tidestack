package com.ouroboros.rpc.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class DynamicAccessTokenRpcAuthCredentialTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(DynamicAccessTokenRPCAuthCredential.class);
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    resetRuntimeState();
  }

  @AfterEach
  void tearDown() {
    tearDownAppender();
    resetRuntimeState();
  }

  @Test
  void applyToRequest_fetchesTokenThroughExecutorAndCachesIt() {
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("token-1", 7200), 18L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);

    DynamicAccessTokenRPCAuthCredential credential = credential("testAccessTokenCredential", accessToken -> {
    });

    HttpRPCRequest firstRequest = new HttpRPCRequest();
    assertTrue(credential.applyToRequest(firstRequest, Collections.emptyMap()).isSuccess());
    assertEquals("Bearer token-1", firstRequest.getHeaders().get("Authorization"));

    HttpRPCRequest secondRequest = new HttpRPCRequest();
    assertTrue(credential.applyToRequest(secondRequest, Collections.emptyMap()).isSuccess());
    assertEquals("Bearer token-1", secondRequest.getHeaders().get("Authorization"));

    ArgumentCaptor<RpcInvocationRequest> captor = ArgumentCaptor.forClass(RpcInvocationRequest.class);
    verify(executor, times(1)).invoke(captor.capture());
    RpcInvocationRequest request = captor.getValue();
    assertEquals("tokenModel", request.getModelName());
    assertTrue(request.isInternalCall());
    assertEquals("token-fetch", request.getPurpose());
    assertEquals(Collections.emptyMap(), request.getParameters());
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L, 1_600_000_000L, 1_600_000_000_000L})
  void applyToRequest_fetchesAgainWhenTokenLifetimeIsNotPositive(long expiresIn) {
    AtomicInteger fetches = new AtomicInteger();
    DynamicAccessTokenRPCAuthCredential credential = new DynamicAccessTokenRPCAuthCredential(
        metadata("expiredTokenCredential", accessToken -> {}), key -> null,
        request -> RpcInvocationResult.success(response("token-" + fetches.incrementAndGet(), expiresIn), 1L),
        new MemoryCacheManager());

    HttpRPCRequest first = new HttpRPCRequest();
    assertTrue(credential.applyToRequest(first, Collections.emptyMap()).isSuccess());
    assertEquals("Bearer token-1", first.getHeaders().get("Authorization"));

    HttpRPCRequest second = new HttpRPCRequest();
    assertTrue(credential.applyToRequest(second, Collections.emptyMap()).isSuccess());
    assertEquals("Bearer token-2", second.getHeaders().get("Authorization"));
    assertEquals(2, fetches.get());
  }

  @Test
  void applyToRequest_reportsTokenFetchFailuresWithCredentialAndTokenModelContext() {
    resetRuntimeState();
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);

    DynamicAccessTokenRPCAuthCredential missingTokenModelCredential = credential("missingTokenModelCredential", accessToken -> {
      ((Map<String, Object>) accessToken.get("request")).remove("modelName");
    });
    AuthenticateException missingTokenModel = assertAuthenticateFailure(
        missingTokenModelCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(missingTokenModel.getMessage().contains("missingTokenModelCredential")),
        () -> assertTrue(missingTokenModel.getMessage().contains("tokenModelName")),
        () -> assertTrue(missingTokenModel.getMessage().contains("未配置令牌请求模型"))
    );
    verifyNoInteractions(executor);

    resetRuntimeState();
    DynamicAccessTokenRPCAuthCredential missingExecutorCredential = credential("missingExecutorCredential", accessToken -> {
    });
    AuthenticateException missingExecutor = assertAuthenticateFailure(
        missingExecutorCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(missingExecutor.getMessage().contains("missingExecutorCredential")),
        () -> assertTrue(missingExecutor.getMessage().contains("tokenModel")),
        () -> assertTrue(missingExecutor.getMessage().contains("RPC调用执行器不可用"))
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential recursiveCredential = credential("recursiveCredential", accessToken -> {
    });
    AtomicReference<Throwable> recursiveCause = new AtomicReference<>();
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenAnswer(invocation -> {
          var inner = recursiveCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap());
          recursiveCause.set(inner.getCause());
          return RpcInvocationResult.failure(inner.getCause());
        });
    AuthenticateException recursiveFailure = assertAuthenticateFailure(
        recursiveCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(recursiveFailure.getMessage().contains("recursiveCredential")),
        () -> assertTrue(recursiveFailure.getMessage().contains("tokenModel")),
        () -> assertTrue(recursiveFailure.getMessage().contains("递归调用")),
        () -> assertSame(recursiveCause.get(), recursiveFailure.getCause())
    );
    verify(executor, times(1)).invoke(any(RpcInvocationRequest.class));

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class))).thenReturn(null);
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential nullResultCredential = credential("nullResultCredential", accessToken -> {
    });
    AuthenticateException nullResult = assertAuthenticateFailure(
        nullResultCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(nullResult.getMessage().contains("nullResultCredential")),
        () -> assertTrue(nullResult.getMessage().contains("tokenModel")),
        () -> assertTrue(nullResult.getMessage().contains("令牌请求未返回结果"))
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("   ", 60), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential blankTokenCredential = credential("blankTokenCredential", accessToken -> {
    });
    AuthenticateException blankToken = assertAuthenticateFailure(
        blankTokenCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(blankToken.getMessage().contains("blankTokenCredential")),
        () -> assertTrue(blankToken.getMessage().contains("tokenModel")),
        () -> assertTrue(blankToken.getMessage().contains("$.access_token")),
        () -> assertTrue(blankToken.getMessage().contains("访问令牌"))
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response(null, 60), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential missingTokenCredential = credential("missingTokenCredential", accessToken -> {
    });
    AuthenticateException missingToken = assertAuthenticateFailure(
        missingTokenCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(missingToken.getMessage().contains("missingTokenCredential")),
        () -> assertTrue(missingToken.getMessage().contains("tokenModel")),
        () -> assertTrue(missingToken.getMessage().contains("$.access_token")),
        () -> assertTrue(missingToken.getMessage().contains("未解析到有效访问令牌"))
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("token-1", 60), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential invalidJsonPathCredential = credential("invalidJsonPathCredential", accessToken -> {
      accessToken.put("accessTokenPath", "$['access_token'");
    });
    AuthenticateException invalidJsonPath = assertAuthenticateFailure(
        invalidJsonPathCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(invalidJsonPath.getMessage().contains("invalidJsonPathCredential")),
        () -> assertTrue(invalidJsonPath.getMessage().contains("tokenModel")),
        () -> assertTrue(invalidJsonPath.getMessage().contains("$['access_token'")),
        () -> assertNotNull(invalidJsonPath.getCause())
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(Collections.singletonMap("session", "value"), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential autoDetectMissCredential = credential("autoDetectMissCredential", accessToken -> {
      accessToken.put("accessTokenPath", "");
    });
    AuthenticateException autoDetectMiss = assertAuthenticateFailure(
        autoDetectMissCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(autoDetectMiss.getMessage().contains("autoDetectMissCredential")),
        () -> assertTrue(autoDetectMiss.getMessage().contains("tokenModel")),
        () -> assertTrue(autoDetectMiss.getMessage().contains("自动识别")),
        () -> assertTrue(autoDetectMiss.getMessage().contains("访问令牌"))
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(Collections.singletonMap("session", "value"), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential missingConfiguredPathCredential = credential("missingConfiguredPathCredential",
        accessToken -> {
        });
    AuthenticateException missingConfiguredPath = assertAuthenticateFailure(
        missingConfiguredPathCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(missingConfiguredPath.getMessage().contains("missingConfiguredPathCredential")),
        () -> assertTrue(missingConfiguredPath.getMessage().contains("tokenModel")),
        () -> assertTrue(missingConfiguredPath.getMessage().contains("$.access_token")),
        () -> assertNotNull(missingConfiguredPath.getCause())
    );
  }

  @Test
  void applyToRequest_normalizesScalarTokenValuesAndRejectsStructuredValues() {
    resetRuntimeState();
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response(123456, 120), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential numericTokenCredential = credential("numericTokenCredential", accessToken -> {
    });
    HttpRPCRequest numericRequest = new HttpRPCRequest();
    assertTrue(numericTokenCredential.applyToRequest(numericRequest, Collections.emptyMap()).isSuccess());
    assertEquals("Bearer 123456", numericRequest.getHeaders().get("Authorization"));

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(responseWithTokenOnly("token_flag", Boolean.TRUE), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential booleanTokenCredential = credential("booleanTokenCredential", accessToken -> {
      accessToken.put("accessTokenPath", "");
      Map<String, Object> expiresConfig = (Map<String, Object>) accessToken.get("expiresConfig");
      expiresConfig.put("expiresValueSource", "config");
    });
    HttpRPCRequest booleanRequest = new HttpRPCRequest();
    assertTrue(booleanTokenCredential.applyToRequest(booleanRequest, Collections.emptyMap()).isSuccess());
    assertEquals("Bearer true", booleanRequest.getHeaders().get("Authorization"));

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response(Collections.singletonMap("value", "nested"), 120), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential objectTokenCredential = credential("objectTokenCredential", accessToken -> {
    });
    AuthenticateException objectToken = assertAuthenticateFailure(
        objectTokenCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(objectToken.getMessage().contains("objectTokenCredential")),
        () -> assertTrue(objectToken.getMessage().contains("$.access_token")),
        () -> assertTrue(objectToken.getMessage().contains("标量"))
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response(Arrays.asList("a", "b"), 120), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential arrayTokenCredential = credential("arrayTokenCredential", accessToken -> {
    });
    AuthenticateException arrayToken = assertAuthenticateFailure(
        arrayTokenCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertTrue(arrayToken.getMessage().contains("arrayTokenCredential")),
        () -> assertTrue(arrayToken.getMessage().contains("$.access_token")),
        () -> assertTrue(arrayToken.getMessage().contains("标量"))
    );
  }

  @Test
  void applyToRequest_warnsAndFallsBackWhenExpiresParsingDegrades() throws InterruptedException {
    logAppender = attachLogAppender();

    resetRuntimeState();
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("token-string-expire", "7200"), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential numericStringExpiresCredential = credential("numericStringExpiresCredential",
        accessToken -> {
        });
    assertTrue(numericStringExpiresCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    assertTrue(numericStringExpiresCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    verify(executor, times(1)).invoke(any(RpcInvocationRequest.class));
    assertTrue(warningEvents().isEmpty());

    clearWarnings();
    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(responseWithTokenOnly("access_token", "token-auto-expire-miss"), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential autoDetectExpiresMissCredential = credential("autoDetectExpiresMissCredential",
        accessToken -> {
          Map<String, Object> expiresConfig = (Map<String, Object>) accessToken.get("expiresConfig");
          expiresConfig.put("expiresPath", "");
          expiresConfig.put("expiresIn", 30L);
        });
    assertTrue(autoDetectExpiresMissCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    assertTrue(autoDetectExpiresMissCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    verify(executor, times(1)).invoke(any(RpcInvocationRequest.class));
    assertSingleWarning("autoDetectExpiresMissCredential", "自动识别过期时间失败", "自动识别过期时间失败",
        "默认过期时间", "token-auto-expire-miss");

    clearWarnings();
    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("token-default-fallback", "expires-secret-default"), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential defaultFallbackCredential = credential("defaultFallbackCredential", accessToken -> {
      Map<String, Object> expiresConfig = (Map<String, Object>) accessToken.get("expiresConfig");
      expiresConfig.put("expiresIn", 30L);
    });
    assertTrue(defaultFallbackCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    assertTrue(defaultFallbackCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    verify(executor, times(1)).invoke(any(RpcInvocationRequest.class));
    assertSingleWarning("defaultFallbackCredential", "$.expires_in", "不是有效数字",
        "默认过期时间", "expires-secret-default");

    clearWarnings();
    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("token-zero-fallback", "expires-secret-zero"), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential zeroFallbackCredential = credential("zeroFallbackCredential", accessToken -> {
    });
    assertTrue(zeroFallbackCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    Thread.sleep(100L);
    assertTrue(zeroFallbackCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    verify(executor, times(2)).invoke(any(RpcInvocationRequest.class));
    assertEquals(2, warningEvents().size());
    assertWarningContainsContextButNotPayload(warningEvents().get(0), "zeroFallbackCredential", "$.expires_in",
        "不是有效数字", "回退值[0ms]", "expires-secret-zero");
    assertWarningContainsContextButNotPayload(warningEvents().get(1), "zeroFallbackCredential", "$.expires_in",
        "不是有效数字", "回退值[0ms]", "expires-secret-zero");

    clearWarnings();
    assertExpiresFallbackWarning(
        "objectExpiresCredential",
        Collections.singletonMap("secret", "expires-secret-object"),
        "类型[",
        "expires-secret-object"
    );
    clearWarnings();
    assertExpiresFallbackWarning(
        "arrayExpiresCredential",
        Collections.singletonList("expires-secret-array"),
        "类型[",
        "expires-secret-array"
    );
    clearWarnings();
    assertExpiresFallbackWarning(
        "booleanExpiresCredential",
        Boolean.TRUE,
        "类型[Boolean]不受支持",
        "true"
    );
  }

  @Test
  void applyToRequest_wrapsDownstreamFailuresWithLocalContextAndCause() {
    resetRuntimeState();
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    RuntimeException upstreamFailure = new RuntimeException("上游执行失败");
    when(executor.invoke(any(RpcInvocationRequest.class))).thenReturn(RpcInvocationResult.failure(upstreamFailure));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential credential = credential("wrappedFailureCredential", accessToken -> {
    });
    AuthenticateException wrappedFailure = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertEquals(
            "认证凭据[wrappedFailureCredential]动态访问令牌获取失败[tokenModelName=tokenModel]：令牌请求执行失败，原因:上游执行失败",
            wrappedFailure.getMessage()),
        () -> assertSame(upstreamFailure, wrappedFailure.getCause())
    );

    resetRuntimeState();
    executor = mock(RpcInvocationExecutor.class);
    RuntimeException messageLessFailure = new RuntimeException();
    when(executor.invoke(any(RpcInvocationRequest.class))).thenReturn(RpcInvocationResult.failure(messageLessFailure));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential messageLessCredential = credential("messageLessFailureCredential", accessToken -> {
    });
    AuthenticateException messageLessWrapped = assertAuthenticateFailure(
        messageLessCredential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));
    assertAll(
        () -> assertEquals(
            "认证凭据[messageLessFailureCredential]动态访问令牌获取失败[tokenModelName=tokenModel]：令牌请求执行失败",
            messageLessWrapped.getMessage()),
        () -> assertSame(messageLessFailure, messageLessWrapped.getCause())
    );
  }

  @SuppressWarnings("unchecked")
  private DynamicAccessTokenRPCAuthCredential credential(String credentialName, Consumer<Map<String, Object>> accessTokenCustomizer) {
    return new DynamicAccessTokenRPCAuthCredential(metadata(credentialName, accessTokenCustomizer), key -> null);
  }

  @SuppressWarnings("unchecked")
  private RPCCredentialMetadata metadata(String credentialName, Consumer<Map<String, Object>> accessTokenCustomizer) {
    RPCCredentialMetadata metadata = new RPCCredentialMetadata();
    metadata.setName(credentialName);
    metadata.setType("AccessToken");
    metadata.setEnabled(true);

    Map<String, Object> accessToken = new HashMap<>();
    accessToken.put("mode", "dynamic");
    Map<String, Object> request = new HashMap<>();
    request.put("modelName", "tokenModel");
    accessToken.put("request", request);
    accessToken.put("accessTokenPath", "$.access_token");

    Map<String, Object> expiresConfig = new HashMap<>();
    expiresConfig.put("expiresValueSource", "response");
    expiresConfig.put("expiresPath", "$.expires_in");
    accessToken.put("expiresConfig", expiresConfig);
    accessTokenCustomizer.accept(accessToken);

    metadata.setExtraProps(Collections.singletonMap("accessToken", accessToken));
    return metadata;
  }

  private Map<String, Object> response(Object accessToken, Object expiresIn) {
    Map<String, Object> response = new HashMap<>();
    response.put("access_token", accessToken);
    response.put("expires_in", expiresIn);
    return response;
  }

  private Map<String, Object> responseWithTokenOnly(String key, Object tokenValue) {
    Map<String, Object> response = new HashMap<>();
    response.put(key, tokenValue);
    return response;
  }

  private AuthenticateException assertAuthenticateFailure(io.vavr.control.Try<HttpRPCRequest> result) {
    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof AuthenticateException);
    return (AuthenticateException) result.getCause();
  }

  private void resetRuntimeState() {
    CacheManager.getCacheManager().clear();
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(null);
  }

  private ListAppender<ILoggingEvent> attachLogAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private void tearDownAppender() {
    if (logAppender != null) {
      logger.detachAppender(logAppender);
      logAppender.stop();
      logAppender = null;
    }
  }

  private List<ILoggingEvent> warningEvents() {
    if (logAppender == null) {
      return Collections.emptyList();
    }
    List<ILoggingEvent> warnings = new ArrayList<>();
    for (ILoggingEvent event : logAppender.list) {
      if (event.getLevel() == Level.WARN) {
        warnings.add(event);
      }
    }
    return warnings;
  }

  private void clearWarnings() {
    if (logAppender != null) {
      logAppender.list.clear();
    }
  }

  private void assertSingleWarning(
      String credentialName,
      String branchContext,
      String reasonFragment,
      String fallbackContext,
      String rawPayloadToken
  ) {
    List<ILoggingEvent> warnings = warningEvents();
    assertEquals(1, warnings.size());
    assertWarningContainsContextButNotPayload(
        warnings.get(0), credentialName, branchContext, reasonFragment, fallbackContext, rawPayloadToken);
  }

  private void assertWarningContainsContextButNotPayload(
      ILoggingEvent warning,
      String credentialName,
      String branchContext,
      String reasonFragment,
      String fallbackContext,
      String rawPayloadToken
  ) {
    assertAll(
        () -> assertTrue(warning.getFormattedMessage().contains(credentialName)),
        () -> assertTrue(warning.getFormattedMessage().contains(branchContext)),
        () -> assertTrue(warning.getFormattedMessage().contains(reasonFragment)),
        () -> assertTrue(warning.getFormattedMessage().contains(fallbackContext)),
        () -> assertFalse(warning.getFormattedMessage().contains(rawPayloadToken))
    );
  }

  @SuppressWarnings("unchecked")
  private void assertExpiresFallbackWarning(
      String credentialName,
      Object expiresValue,
      String reasonFragment,
      String rawPayloadToken
  ) {
    resetRuntimeState();
    RpcInvocationExecutor executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(response("token-" + credentialName, expiresValue), 1L));
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    DynamicAccessTokenRPCAuthCredential credential = credential(credentialName, accessToken -> {
      Map<String, Object> expiresConfig = (Map<String, Object>) accessToken.get("expiresConfig");
      expiresConfig.put("expiresIn", 30L);
    });
    assertTrue(credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    assertTrue(credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()).isSuccess());
    verify(executor, times(1)).invoke(any(RpcInvocationRequest.class));
    assertSingleWarning(credentialName, "$.expires_in", reasonFragment, "默认过期时间", rawPayloadToken);
  }
}
