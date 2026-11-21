package com.ouroboros.rpc.security;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.jayway.jsonpath.JsonPath;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.Json;

public class DynamicAccessTokenRPCAuthCredential extends AbstractAccessTokenRPCAuthCredential {
  private static final String CACHE_NAME = "ouroboros:rpc:access-token";
  private static final Logger logger = LoggerFactory.getLogger(DynamicAccessTokenRPCAuthCredential.class);
  private final Function<RpcInvocationRequest, RpcInvocationResult> invocation;
  private final CacheManager tokenCache;
  private final String tokenModelName;
  private final String accessTokenPath, expiresPath;
  private final String expiresValueSource;
  private final Long defaultExpireInMs;
  // Old in-flight requests can only populate their own credential generation.
  private final String cacheKey;

  public DynamicAccessTokenRPCAuthCredential(RPCCredentialMetadata metadata, Function<String, String> configGetter) {
    this(metadata, configGetter, request -> {
      var executor = AccessTokenRpcAuthCredential.getRpcInvocationExecutor();
      if (executor == null) {
        throw new AuthenticateException("RPC调用执行器不可用");
      }
      return executor.invoke(new RpcInvocationRequest(request.getModelName(), Collections.emptyMap(),
          request.isInternalCall(), request.getPurpose(), request.getOptions()));
    }, CacheManager.getCacheManager());
  }

  public DynamicAccessTokenRPCAuthCredential(RPCCredentialMetadata metadata, Function<String, String> configGetter,
      Function<RpcInvocationRequest, RpcInvocationResult> invocation, CacheManager tokenCache) {
    super(metadata, configGetter);
    this.invocation = invocation;
    this.tokenCache = tokenCache;
    this.cacheKey = getName() + ":" + java.util.UUID.randomUUID();

    tokenModelName = getExtraPropDeep(String.class, "accessToken", "request", "modelName").orElse("");
    accessTokenPath = getExtraPropDeep(String.class, "accessToken", "accessTokenPath").orElse("");
    expiresValueSource = getExtraPropDeep(String.class, "accessToken", "expiresConfig", "expiresValueSource").orElse("response");
    expiresPath = getExtraPropDeep(String.class, "accessToken", "expiresConfig", "expiresPath").orElse("");
    String unitStr = getExtraPropDeep(String.class, "accessToken", "expiresConfig", "unit").orElse("second");
    // 倍率
    Integer scale = "millisecond".equals(unitStr) ? 1 : 1000;
    defaultExpireInMs = getExtraPropDeep("accessToken", "expiresConfig", "expiresIn").map(value -> Long.parseLong(value.toString())).orElse(0L) * scale;
  }

  @Override
  protected String getAccessToken() {
    return getAccessToken(Collections.emptyMap());
  }

  @Override
  protected String getAccessToken(Map<String, Object> context) {
    Optional<Object> optional = tokenCache.get(CACHE_NAME, cacheKey);
    if (optional.isPresent()) {
      return (String) optional.get();
    }

    Tuple2<String, Long> token = fetchAccessToken(context);
    // 立即过期的令牌只用于本次请求，不进入缓存。
    if (token._2() > 0) {
      tokenCache.put(CACHE_NAME, cacheKey, token._1(), token._2());
    }
    return token._1();
  }

  /**
   * 获取AccessToken
   *
   * @return
   */
  private Tuple2<String, Long> fetchAccessToken(Map<String, Object> context) {
    if (StringUtils.isBlank(tokenModelName)) {
      throw tokenFetchFailure("未配置令牌请求模型");
    }
    if (!enterAccessTokenFetch()) {
      throw tokenFetchFailure("动态令牌获取发生递归调用");
    }
    try {
      RpcInvocationResult result;
      try {
        result = invocation.apply(new RpcInvocationRequest(
            tokenModelName,
            context,
            true,
            "token-fetch",
            Collections.emptyMap()
        ));
      } catch (Throwable throwable) {
        throw tokenFetchFailure("令牌请求执行失败", throwable);
      }
      if (result == null) {
        throw tokenFetchFailure("令牌请求未返回结果");
      }
      if (result.isFailure()) {
        throw tokenFetchFailure("令牌请求执行失败", result.getError());
      }
      Map<String, Object> responseMap = toResponseMap(result.getPayload());
      String accessToken = parseToken(responseMap);
      Long expireIn = parseExpires(responseMap);
      return Tuple.of(accessToken, expireIn);
    } finally {
      exitAccessTokenFetch();
    }
  }

  private Map<String, Object> toResponseMap(Object response) {
    if (response instanceof Map) {
      Map<String, Object> result = safeCastToMap(response);
      if (result != null) {
        return result;
      }
    }
    if (response instanceof String responseString) {
      return Json.toMap(responseString);
    }
    return Collections.emptyMap();
  }

  private String parseToken(Map<String, Object> response) {
    if (StringUtils.isBlank(accessTokenPath)) {
      for (Map.Entry<String, Object> entry : response.entrySet()) {
        if (StringUtils.containsIgnoreCase(entry.getKey(), "token")) {
          return normalizeTokenValue(entry.getValue(), "自动识别访问令牌键[" + entry.getKey() + "]");
        }
      }
      throw tokenFetchFailure("自动识别访问令牌失败");
    }
    Object value;
    try {
      value = JsonPath.read(Json.toJsonString(response), accessTokenPath);
    } catch (Throwable throwable) {
      throw tokenFetchFailure("accessTokenPath[" + accessTokenPath + "]解析访问令牌失败", throwable);
    }
    return normalizeTokenValue(value, "accessTokenPath[" + accessTokenPath + "]");
  }

  private Long parseExpires(Map<String, Object> response) {
    if (!"response".equals(expiresValueSource)) {
      return defaultExpireInMs;
    }
    if (StringUtils.isBlank(expiresPath)) {
      for (Map.Entry<String, Object> entry : response.entrySet()) {
        if (StringUtils.containsIgnoreCase(entry.getKey(), "expire")) {
          return inferExpiresIn(entry.getValue(), "自动识别过期时间键[" + entry.getKey() + "]");
        }
      }
      return warnAndFallbackExpires("自动识别过期时间失败");
    }
    Object expiresValue;
    try {
      expiresValue = JsonPath.read(Json.toJsonString(response), expiresPath);
    } catch (Throwable exception) {
      return warnAndFallbackExpires("expiresPath[" + expiresPath + "]解析失败");
    }
    return inferExpiresIn(expiresValue, "expiresPath[" + expiresPath + "]");
  }

  /**
   * 推断过期时间
   * 一般情况下，expiresValue是一个秒数，但是为了防止有些接口返回的是时间戳，所以这里做了一个推断
   *
   * @param expiresValue
   * @return 过期时间
   */
  private Long inferExpiresIn(Object expiresValue, String branchContext) {
    if (expiresValue == null) {
      return warnAndFallbackExpires(branchContext + "为空");
    }

    Long expiresValueLong;
    if (expiresValue instanceof Number number) {
      expiresValueLong = number.longValue();
    } else if (expiresValue instanceof String expiresValueString && StringUtils.isNotBlank(expiresValueString)) {
      try {
        expiresValueLong = Long.parseLong(expiresValueString.trim());
      } catch (NumberFormatException exception) {
        return warnAndFallbackExpires(branchContext + "不是有效数字");
      }
    } else {
      return warnAndFallbackExpires(branchContext + "类型[" + expiresValue.getClass().getSimpleName() + "]不受支持");
    }

    // 如果是毫秒的话，返回剩余毫秒数
    if (expiresValueLong > 1000000000000L) {
      return expiresValueLong - System.currentTimeMillis();
    }
    // 如果是秒的时间戳，返回剩余毫秒数
    if (expiresValueLong > 1000000000L) {
      return expiresValueLong * 1000 - System.currentTimeMillis();
    }
    // 如果不是时间戳，按秒看待
    return expiresValueLong * 1000;
  }

  private String normalizeTokenValue(Object value, String branchContext) {
    if (value == null) {
      throw tokenFetchFailure(branchContext + "未解析到有效访问令牌");
    }
    if (value instanceof Map || value instanceof Iterable<?> || value.getClass().isArray()) {
      throw tokenFetchFailure(branchContext + "解析结果不是标量值");
    }

    String accessToken = value.toString();
    if (StringUtils.isBlank(accessToken)) {
      throw tokenFetchFailure(branchContext + "未解析到有效访问令牌");
    }
    return accessToken;
  }

  private Long warnAndFallbackExpires(String branchContext) {
    long fallbackValue = defaultExpireInMs != null ? defaultExpireInMs : 0L;
    String fallbackContext = fallbackValue > 0
        ? "默认过期时间[" + fallbackValue + "ms]"
        : "回退值[0ms]";
    logger.warn("认证凭据[{}]动态访问令牌过期时间解析降级[tokenModelName={}]：{}，使用{}",
        getName(), tokenModelName, branchContext, fallbackContext);
    return fallbackValue;
  }

  private AuthenticateException tokenFetchFailure(String detail) {
    return new AuthenticateException(tokenFetchMessage(detail));
  }

  private AuthenticateException tokenFetchFailure(String detail, Throwable cause) {
    String message = tokenFetchMessage(detail);
    String causeMessage = cause == null ? null : StringUtils.trimToNull(cause.getMessage());
    if (causeMessage != null) {
      message = message + "，原因:" + causeMessage;
    }
    return new AuthenticateException(message, cause);
  }

  private String tokenFetchMessage(String detail) {
    return "认证凭据[" + getName() + "]动态访问令牌获取失败[tokenModelName=" + tokenModelName + "]：" + detail;
  }

  private Map<String, Object> safeCastToMap(Object value) {
    if (!(value instanceof Map<?, ?> raw)) {
      return null;
    }
    var copy = new java.util.LinkedHashMap<String, Object>();
    for (var entry : raw.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        return null;
      }
      copy.put(key, entry.getValue());
    }
    return copy;
  }
}
