package com.ouroboros.rpc.dev.service.testing;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.ouroboros.rpc.execution.RpcHttpExchange;
import com.ouroboros.rpc.util.ExpressionUtils;
import com.ouroboros.util.Json;

/** The sole boundary from raw exchanges to public test evidence. */
final class RpcModelTestRedactor {
  private static final String MASK = "***";
  private static final Pattern SENSITIVE = Pattern.compile(
      "(?i).*(authorization|cookie|password|passwd|secret|token|signature|api[-_]?key|access[-_]?key|credential).*" );
  private final Set<String> values = new java.util.HashSet<>();
  private final Set<String> names = new java.util.HashSet<>();
  private final int limit;

  RpcModelTestRedactor(int limit) { this.limit = limit; }

  void addSecret(Object value) {
    if (value instanceof Map<?, ?> map) { map.values().forEach(this::addSecret); }
    else if (value instanceof List<?> list) { list.forEach(this::addSecret); }
    else if (value != null && !value.toString().isEmpty()) {
      String text = value.toString();
      values.add(text);
      values.add(URLEncoder.encode(text, StandardCharsets.UTF_8));
      values.add(java.util.Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
    }
  }

  void credential(Object value) {
    if (value instanceof Map<?, ?> map) {
      if (map.get("name") instanceof String name && map.containsKey("in")) { names.add(name.toLowerCase(Locale.ROOT)); }
      for (var entry : map.entrySet()) {
        if (entry.getValue() instanceof String text && (sensitive(String.valueOf(entry.getKey()))
            || Set.of("value", "username").contains(String.valueOf(entry.getKey())))) {
          for (var segment : ExpressionUtils.segments(text)) {
            if (segment.kind() == ExpressionUtils.Kind.LITERAL) { addSecret(segment.value()); }
          }
        } else { credential(entry.getValue()); }
      }
    } else if (value instanceof List<?> list) { list.forEach(this::credential); }
  }

  RpcModelTestResult.Attempt exchange(RpcHttpExchange raw) {
    boolean auth = "token-fetch".equals(raw.purpose());
    if (auth && raw.responseBody() != null) {
      var decoded = Json.tryToMap(new String(raw.responseBody(), StandardCharsets.UTF_8));
      if (decoded.isSuccess()) { addSecret(decoded.get()); }
    }
    var request = new RpcModelTestResult.Request(raw.method(), url(raw.url()), headers(raw.requestHeaders()), Map.of(),
        body(raw.requestBody(), contentType(raw.requestHeaders()), auth), true);
    var response = raw.status() == null ? null : new RpcModelTestResult.Response(raw.status(), headers(raw.responseHeaders()),
        body(raw.responseBody(), contentType(raw.responseHeaders()), auth));
    return new RpcModelTestResult.Attempt(raw.modelName(), raw.attempt(), raw.durationMs(), request, response,
        raw.errorStage() == null ? null : error(raw.errorStage()));
  }

  Object sanitize(Object value) {
    if (value instanceof Map<?, ?> map) {
      var sanitized = new LinkedHashMap<String, Object>();
      map.forEach((key, item) -> sanitized.put(String.valueOf(key), sensitive(String.valueOf(key)) ? MASK
          : "url".equals(key) && item instanceof String url ? url(url) : sanitize(item)));
      return sanitized;
    }
    if (value instanceof List<?> list) { return list.stream().map(this::sanitize).toList(); }
    return value instanceof String text ? text(text) : value;
  }

  private String text(String text) {
    String result = text;
    for (var secret : values.stream().sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList()) {
      result = result.replace(secret, MASK);
    }
    return result;
  }

  private boolean sensitive(String name) { return SENSITIVE.matcher(name).matches() || names.contains(name.toLowerCase(Locale.ROOT)); }

  private Map<String, List<String>> headers(Map<String, List<String>> headers) {
    var result = new LinkedHashMap<String, List<String>>();
    headers.forEach((name, items) -> result.put(name, sensitive(name) ? List.of(MASK) : items.stream().map(this::text).toList()));
    return result;
  }

  private String url(String value) {
    try {
      var uri = URI.create(value);
      String query = uri.getRawQuery();
      if (query != null) {
        var parts = new ArrayList<String>();
        for (String part : query.split("&")) {
          int split = part.indexOf('=');
          String name = split < 0 ? part : part.substring(0, split);
          String decoded = java.net.URLDecoder.decode(name, StandardCharsets.UTF_8);
          parts.add(sensitive(decoded) ? name + "=" + MASK : text(part));
        }
        query = String.join("&", parts);
      }
      return text(uri.getScheme() + "://" + (uri.getRawUserInfo() == null ? "" : MASK + "@") + uri.getHost()
          + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) + uri.getRawPath() + (query == null ? "" : "?" + query));
    } catch (IllegalArgumentException error) {
      return "（URL 无法安全展示）";
    }
  }

  private RpcModelTestResult.Body body(byte[] bytes, String contentType, boolean authentication) {
    if (bytes == null) { return null; }
    if (authentication) { return new RpcModelTestResult.Body(null, contentType, false, bytes.length, "认证正文已隐藏"); }
    if (contentType == null || !(contentType.toLowerCase(Locale.ROOT).contains("json")
        || contentType.toLowerCase(Locale.ROOT).startsWith("text/"))) {
      return new RpcModelTestResult.Body(null, contentType, false, bytes.length, "非文本正文已省略");
    }
    String body = new String(bytes, StandardCharsets.UTF_8);
    if (contentType.toLowerCase(Locale.ROOT).contains("json")) {
      String rawBody = body;
      var parsed = io.vavr.control.Try.of(() -> Json.mapperCopy().readValue(rawBody, Object.class));
      if (parsed.isFailure()) {
        return new RpcModelTestResult.Body(null, contentType, false, bytes.length, "无法安全解析的 JSON 正文已省略");
      }
      body = Json.toJsonString(sanitize(parsed.get()));
    } else {
      // Unstructured target output may contain unknown credentials; do not guess its grammar.
      return new RpcModelTestResult.Body(null, contentType, false, bytes.length, "非结构化正文已省略");
    }
    byte[] sanitized = body.getBytes(StandardCharsets.UTF_8);
    boolean truncated = sanitized.length > limit;
    if (truncated) {
      int end = limit;
      while (end > 0 && (sanitized[end] & 0xc0) == 0x80) { end--; }
      body = new String(sanitized, 0, end, StandardCharsets.UTF_8);
    }
    return new RpcModelTestResult.Body(body, contentType, truncated, bytes.length, null);
  }

  private String contentType(Map<String, List<String>> headers) {
    return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase("content-type"))
        .flatMap(entry -> entry.getValue().stream()).findFirst().orElse(null);
  }

  static RpcModelTestResult.Error error(String stage) {
    return switch (stage) {
      case "HTTP_STATUS" -> new RpcModelTestResult.Error("RESPONSE", "REMOTE_HTTP_ERROR", "目标服务返回非 2xx 状态", null, null, true);
      case "RESPONSE_DECODE" -> new RpcModelTestResult.Error("RESPONSE", "DECODE_FAILED", "目标响应解码失败", null, null, true);
      case "RESPONSE_LIMIT" -> new RpcModelTestResult.Error("RESPONSE", "RESPONSE_LIMIT", "目标响应超过传输限制", null, null, true);
      case "AUTHENTICATION" -> new RpcModelTestResult.Error("AUTH", "AUTHENTICATION_FAILED", "认证或令牌获取失败", null, null, true);
      case "REQUEST_BUILD" -> new RpcModelTestResult.Error("EXPRESSION", "EVALUATION_FAILED", "请求字段求值失败，请检查参数", null, null, true);
      case "REQUEST_LIMIT" -> new RpcModelTestResult.Error("BUILD", "REQUEST_INVALID", "请求正文超过限制", null, null, true);
      case "TIMEOUT" -> new RpcModelTestResult.Error("TRANSPORT", "DEADLINE_EXCEEDED", "调用超时或执行预算已耗尽", null, null, true);
      default -> new RpcModelTestResult.Error("TRANSPORT", "CALL_FAILED", "目标调用失败，请检查连接配置", null, null, true);
    };
  }
}
