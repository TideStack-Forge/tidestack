package com.ouroboros.rpc.security.hmac;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.util.Json;

public class HmacRequestBodySerializer {

  public SerializedBody serialize(HttpRPCRequest request) {
    Object body = request == null ? null : request.getBody();
    String contentType = findContentType(request);
    if (body == null || (body instanceof Map<?, ?> && ((Map<?, ?>) body).isEmpty())) {
      return SerializedBody.empty();
    }
    ensureSupportedBody(contentType, body);
    String resolvedContentType = StringUtils.isBlank(contentType) ? "application/json" : contentType;
    return SerializedBody.of(stableJson(body), resolvedContentType);
  }

  private String stableJson(Object body) {
    return Json.tryToJsonString(normalizeJsonValue(body))
        .getOrElseThrow(throwable -> new AuthenticateException("HMAC签名请求体序列化失败", throwable));
  }

  @SuppressWarnings("unchecked")
  private Object normalizeJsonValue(Object value) {
    rejectBinaryPayload(value);
    if (value instanceof Map<?, ?>) {
      TreeMap<String, Object> sorted = new TreeMap<String, Object>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        sorted.put(String.valueOf(entry.getKey()), normalizeJsonValue(entry.getValue()));
      }
      return sorted;
    }
    if (value != null && value.getClass().isArray()) {
      List<Object> normalized = new ArrayList<Object>();
      int length = Array.getLength(value);
      for (int index = 0; index < length; index++) {
        normalized.add(normalizeJsonValue(Array.get(value, index)));
      }
      return normalized;
    }
    if (value instanceof List<?>) {
      List<Object> normalized = new ArrayList<Object>();
      for (Object item : (List<Object>) value) {
        normalized.add(normalizeJsonValue(item));
      }
      return normalized;
    }
    if (isScalarJsonValue(value)) {
      return value;
    }
    throw new AuthenticateException("HMAC签名暂不支持 unsupported 对象请求体");
  }

  private void ensureSupportedBody(String contentType, Object body) {
    ensureContentTypeSupported(contentType);
    rejectBinaryPayload(body);
    if (StringUtils.isNotBlank(contentType) && !StringUtils.containsIgnoreCase(contentType, "json")) {
      throw new AuthenticateException("HMAC签名暂不支持 content-type[" + contentType + "] 的对象请求体");
    }
  }

  private void rejectBinaryPayload(Object body) {
    if (body instanceof byte[] || isBinaryPayload(body)) {
      throw new AuthenticateException("HMAC签名暂不支持 binary 请求体");
    }
  }

  private boolean isScalarJsonValue(Object value) {
    return value == null
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean;
  }

  private void ensureContentTypeSupported(String contentType) {
    if (StringUtils.isBlank(contentType)) {
      return;
    }
    if (StringUtils.containsIgnoreCase(contentType, "multipart/form-data")) {
      throw new AuthenticateException("HMAC签名暂不支持 content-type[" + contentType + "] 的 multipart 请求体");
    }
    if (StringUtils.containsIgnoreCase(contentType, "application/x-www-form-urlencoded")) {
      throw new AuthenticateException("HMAC签名暂不支持 content-type[" + contentType + "] 的 form-urlencoded 请求体");
    }
    if (StringUtils.containsIgnoreCase(contentType, "octet-stream")) {
      throw new AuthenticateException("HMAC签名暂不支持 content-type[" + contentType + "] 的 binary 请求体");
    }
  }

  private boolean isBinaryPayload(Object body) {
    return body instanceof java.io.File
        || body instanceof java.io.InputStream
        || body instanceof java.io.Reader
        || body instanceof java.nio.ByteBuffer;
  }

  private String findContentType(HttpRPCRequest request) {
    if (request == null) {
      return null;
    }
    for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
      if ("content-type".equalsIgnoreCase(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }
}
