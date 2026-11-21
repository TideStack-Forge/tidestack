package com.ouroboros.rpc.execution.protocol;

import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.web.reactive.function.client.WebClient;

import io.vavr.control.Try;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.exception.RPCException;
import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcHttpExchange;
import com.ouroboros.rpc.execution.RpcExecutionException;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.execution.RpcProtocolInvoker;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.model.OpenApiRpcModelDefinitionBuilder;
import com.ouroboros.rpc.security.RPCAuthCredential;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.security.hmac.HmacSigningSupport;
import com.ouroboros.rpc.security.hmac.SerializedBody;
import com.ouroboros.rpc.util.HttpRpcRequestBuilder;
import com.ouroboros.rpc.util.RpcConfigurationTemplates;

public class OpenApiRpcProtocolInvoker implements RpcProtocolInvoker {
  private final HttpRpcRequestBuilder requestBuilder;
  private final WebClient.Builder webClientBuilder;

  public OpenApiRpcProtocolInvoker(HttpRpcRequestBuilder requestBuilder) {
    this(requestBuilder, WebClient.builder());
  }

  public OpenApiRpcProtocolInvoker(HttpRpcRequestBuilder requestBuilder, WebClient.Builder webClientBuilder) {
    this.requestBuilder = requestBuilder;
    this.webClientBuilder = webClientBuilder;
  }

  @Override
  public boolean supports(RPCModelDefinition definition) {
    return definition != null && "OpenAPI".equalsIgnoreCase(definition.getType());
  }

  @Override
  public RpcInvocationResult invoke(RpcInvocationContext context) {
    long startedAt = System.currentTimeMillis();
    Map<String, Object> requestDefine = extractRequestDefine(context.getDefinition());
    if (requestDefine == null) {
      RpcInvocationResult result = RpcInvocationResult.failure(new RPCException("请求定义不存在"), elapsed(startedAt));
      context.setError(result.getError()).setResult(result);
      return result;
    }

    Try<HttpRPCRequest> requestTry = context.getResources() == null
        ? requestBuilder.buildConfiguredRequest(requestDefine, context.getParameters())
        : HttpRpcRequestBuilder.buildConfiguredRequest(requestDefine, context.getParameters(), context.getResources()::getConfig);
    if (requestTry.isFailure()) {
      RpcInvocationResult result = RpcInvocationResult.failure(
          new RpcExecutionException("REQUEST_BUILD", "构建请求失败", requestTry.getCause()),
          elapsed(startedAt)
      );
      context.setError(result.getError()).setResult(result);
      return result;
    }

    Try<HttpRPCRequest> authenticatedRequestTry = authenticate(context, requestTry.get());
    if (authenticatedRequestTry.isFailure()) {
      RpcInvocationResult result = RpcInvocationResult.failure(
          new RpcExecutionException("AUTHENTICATION", "认证失败", authenticatedRequestTry.getCause()),
          elapsed(startedAt)
      );
      context.setError(result.getError()).setResult(result);
      return result;
    }

    HttpRPCRequest request = authenticatedRequestTry.get();
    context.setProtocolRequest(request);
    try {
      Object serialized = request.getAttribute(SerializedBody.REQUEST_ATTRIBUTE);
      byte[] body;
      if (serialized instanceof SerializedBody signed) {
        applySerializedContentTypeHint(request, signed);
        body = signed.getBodyText().getBytes(StandardCharsets.UTF_8);
      } else {
        body = com.ouroboros.util.Json.toJsonString(request.getBody()).getBytes(StandardCharsets.UTF_8);
        if (StringUtils.isBlank(findHeader(request, "Content-Type"))) {
          request.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        }
      }
      if (context.getResources() != null && body.length > context.getResources().maxRequestBytes()) {
        throw new RpcExecutionException("REQUEST_LIMIT", "请求正文超过限制", null);
      }
      var uri = buildUri(request);
      var attempts = new AtomicInteger();
      Object response = Mono.defer(() -> exchange(context, request, uri, body, attempts.incrementAndGet()))
          .retryWhen(reactor.util.retry.Retry.max(resolveRetry(context))
              .filter(error -> canReplay(context)
                  && (context.getResources() == null || context.getResources().remainingDurationMs() > 0))
              .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
          .block();
      var result = RpcInvocationResult.success(response, elapsed(startedAt));
      context.setProtocolResponse(response).setResult(result);
      return result;
    } catch (Exception exception) {
      var failure = exception instanceof RpcExecutionException ? exception
          : new RpcExecutionException(failureStage(exception), "调用失败", exception);
      var result = RpcInvocationResult.failure(failure, elapsed(startedAt));
      context.setError(result.getError()).setResult(result);
      return result;
    }
  }

  private Mono<Object> exchange(RpcInvocationContext context, HttpRPCRequest request, URI uri, byte[] body, int attempt) {
    long remaining = context.getResources() == null ? Long.MAX_VALUE : context.getResources().remainingDurationMs();
    if (remaining <= 0) {
      return Mono.error(new RpcExecutionException("TIMEOUT", "测试执行预算已耗尽", null));
    }
    long started = System.nanoTime();
    var status = new AtomicReference<Integer>();
    var responseHeaders = new AtomicReference<Map<String, List<String>>>(Map.of());
    var responseBody = new AtomicReference<byte[]>();
    var requestHeaders = new AtomicReference<Map<String, List<String>>>(Map.of());
    var actualUrl = new AtomicReference<>(uri.toString());
    var stage = new AtomicReference<>("TRANSPORT");
    var client = webClientBuilder.clone().filter((outgoing, next) -> {
      var appliedHeaders = new LinkedHashMap<String, List<String>>(outgoing.headers());
      if (!outgoing.cookies().isEmpty()) {
        var cookies = new java.util.ArrayList<String>();
        outgoing.cookies().forEach((key, values) -> values.forEach(value -> cookies.add(key + "=" + value)));
        appliedHeaders.put(HttpHeaders.COOKIE, List.of(String.join("; ", cookies)));
      }
      requestHeaders.set(appliedHeaders);
      actualUrl.set(outgoing.url().toString());
      if (context.getResources() != null) { context.getResources().beforeExchange(outgoing.url()); }
      return next.exchange(outgoing);
    }).build();
    return client.method(HttpMethod.valueOf(request.getMethod().toUpperCase(java.util.Locale.ROOT)))
        .uri(uri)
        .headers(headers -> request.getHeaders().forEach(headers::set))
        .cookies(cookies -> request.getCookies().forEach(cookies::add))
        .bodyValue(body)
        .exchangeToMono(incoming -> {
          status.set(incoming.statusCode().value());
          responseHeaders.set(new LinkedHashMap<>(incoming.headers().asHttpHeaders()));
          stage.set("RESPONSE_LIMIT");
          return incoming.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).flatMap(bytes -> {
            responseBody.set(bytes);
            if (!incoming.statusCode().is2xxSuccessful()) {
              return Mono.error(new RpcExecutionException("HTTP_STATUS", "目标服务返回非成功状态", null));
            }
            if (bytes.length == 0) {
              return Mono.empty();
            }
            stage.set("RESPONSE_DECODE");
            return ClientResponse.create(incoming.statusCode(), incoming.strategies())
                .headers(headers -> headers.addAll(incoming.headers().asHttpHeaders()))
                .body(Flux.just(new DefaultDataBufferFactory().wrap(bytes))).build()
                .bodyToMono(Object.class);
          });
        })
        .timeout(Duration.ofMillis(Math.max(1L, Math.min(resolveTimeoutMs(context), remaining))))
        .doOnSuccess(value -> recordExchange(context, request, attempt, actualUrl.get(), requestHeaders.get(), body,
            status.get(), responseHeaders.get(), responseBody.get(), started, null))
        .doOnError(error -> recordExchange(context, request, attempt, actualUrl.get(), requestHeaders.get(), body,
            status.get(), responseHeaders.get(), responseBody.get(), started,
            error instanceof RpcExecutionException execution ? execution.getStage()
                : error instanceof java.util.concurrent.TimeoutException ? "TIMEOUT"
                : containsLimitFailure(error) ? "RESPONSE_LIMIT" : stage.get()))
        .onErrorMap(error -> error instanceof RpcExecutionException ? error
            : new RpcExecutionException(error instanceof java.util.concurrent.TimeoutException ? "TIMEOUT"
                : containsLimitFailure(error) ? "RESPONSE_LIMIT" : stage.get(), "调用失败", error));
  }

  private boolean containsLimitFailure(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof DataBufferLimitException) { return true; }
    }
    return false;
  }

  private String failureStage(Throwable error) {
    return error instanceof java.util.concurrent.TimeoutException ? "TIMEOUT" : "TRANSPORT";
  }

  private void recordExchange(RpcInvocationContext context, HttpRPCRequest request, int attempt, String url,
      Map<String, List<String>> requestHeaders, byte[] requestBody, Integer status,
      Map<String, List<String>> responseHeaders, byte[] responseBody, long started, String stage) {
    if (context.getResources() != null) {
      context.getResources().onExchange(new RpcHttpExchange(context.getModelName(), context.getPurpose(), attempt,
          request.getMethod(), url, requestHeaders, requestBody, status, responseHeaders, responseBody,
          Math.max(0L, (System.nanoTime() - started) / 1_000_000L), stage));
    }
  }

  private Try<HttpRPCRequest> authenticate(RpcInvocationContext context, HttpRPCRequest request) {
    var definition = context.getDefinition();
    if (definition == null || StringUtils.isBlank(definition.getAuthCredential())) {
      return Try.success(request);
    }
    String credentialName;
    try {
      credentialName = configurationValue(context, definition.getAuthCredential());
    } catch (RuntimeException exception) {
      return Try.failure(exception);
    }
    RPCAuthCredential credential = context.getResources() == null
        ? RPCAuthCredentialCenter.getRpcCredential(credentialName).orElse(null)
        : context.getResources().findCredential(credentialName).orElse(null);
    if (credential == null) {
      return Try.failure(new AuthenticateException("RPC模型[" + definition.getName() + "]引用的认证凭据["
          + definition.getAuthCredential() + "]不存在"));
    }
    Try<?> applied = credential.applyToRequest(request, context.getParameters(), context.getResources());
    return applied.flatMap(value -> value instanceof HttpRPCRequest http ? Try.success(http)
        : Try.failure(new AuthenticateException("认证器返回了不兼容的请求类型")));
  }

  private Map<String, Object> extractRequestDefine(RPCModelDefinition definition) {
    if (!(definition instanceof OpenApiRpcModelDefinitionBuilder.OpenApiRpcModelDefinition)) {
      return null;
    }

    OpenApiRpcModelDefinitionBuilder.OpenApiRpcModelDefinition openApiDefinition =
        (OpenApiRpcModelDefinitionBuilder.OpenApiRpcModelDefinition) definition;
    return openApiDefinition.getRequest();
  }

  private long resolveTimeoutMs(RpcInvocationContext context) {
    Long override = context.getOption(Number.class, "timeoutOverrideMs")
        .map(Number::longValue)
        .orElse(null);
    if (override != null) {
      return override;
    }
    return getExtraNumber(context, "timeout").map(Number::longValue).orElse(3000L);
  }

  private long resolveRetry(RpcInvocationContext context) {
    Integer override = context.getOption(Number.class, "retryOverride")
        .map(Number::intValue)
        .orElse(null);
    if (override != null) {
      return override;
    }
    return getExtraNumber(context, "retry").map(Number::longValue).orElse(0L);
  }

  /** Only a static model contract can permit replay; request parameters and policy cannot. */
  private boolean canReplay(RpcInvocationContext context) {
    var definition = context.getDefinition();
    return definition != null && definition.getExtraProps() != null
        && Boolean.TRUE.equals(definition.getExtraProps().get("retrySafe"));
  }

  private String configurationValue(RpcInvocationContext context, String source) {
    return context.getResources() == null ? requestBuilder.configurationValue(source)
        : RpcConfigurationTemplates.resolve(source, context.getResources()::getConfig);
  }

  private Optional<Number> getExtraNumber(RpcInvocationContext context, String key) {
    var definition = context.getDefinition();
    if (definition == null || definition.getExtraProps() == null) {
      return Optional.empty();
    }
    Object value = definition.getExtraProps().get(key);
    if (value instanceof Number) {
      return Optional.of((Number) value);
    }
    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
      try {
        return Optional.of(Long.parseLong(configurationValue(context, (String) value)));
      } catch (NumberFormatException exception) {
        throw new RPCException("RPC模型[" + definition.getName() + "]的" + key + "必须是整数");
      }
    }
    return Optional.empty();
  }

  private URI buildUri(HttpRPCRequest request) {
    URI uri = HmacSigningSupport.toUri(request);
    if (uri == null) {
      return null;
    }

    String mergedQuery = HmacSigningSupport.mergeQuery(uri, request.getQuery());
    StringBuilder builder = new StringBuilder();
    if (StringUtils.isNotBlank(uri.getScheme())) {
      builder.append(uri.getScheme()).append(':');
    }
    if (StringUtils.isNotBlank(uri.getRawAuthority())) {
      builder.append("//").append(uri.getRawAuthority());
    }
    builder.append(StringUtils.defaultString(uri.getRawPath()));
    if (StringUtils.isNotEmpty(mergedQuery)) {
      builder.append('?').append(mergedQuery);
    }
    if (StringUtils.isNotEmpty(uri.getRawFragment())) {
      builder.append('#').append(uri.getRawFragment());
    }
    return URI.create(builder.toString());
  }

  private long elapsed(long startedAt) {
    return Math.max(0L, System.currentTimeMillis() - startedAt);
  }

  private void applySerializedContentTypeHint(HttpRPCRequest request, SerializedBody serializedBody) {
    if (request == null || serializedBody == null) {
      return;
    }
    if (StringUtils.isNotBlank(findHeader(request, "Content-Type"))) {
      return;
    }
    if (StringUtils.isBlank(serializedBody.getContentType())) {
      return;
    }
    request.header("Content-Type", serializedBody.getContentType());
  }

  private String findHeader(HttpRPCRequest request, String headerName) {
    if (request == null || StringUtils.isBlank(headerName)) {
      return null;
    }
    for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
      if (headerName.equalsIgnoreCase(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }
}
