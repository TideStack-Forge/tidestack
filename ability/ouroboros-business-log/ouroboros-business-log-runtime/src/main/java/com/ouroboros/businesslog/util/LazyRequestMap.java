package com.ouroboros.businesslog.util;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

import io.vavr.Tuple;

import com.ouroboros.util.Json;

public class LazyRequestMap extends LazyMap {
  private final ContentCachingRequestWrapper request;

  public LazyRequestMap(ContentCachingRequestWrapper request) {
    super();
    this.request = request;
    this.addLazyKV("method", request::getMethod);
    this.addLazyKV("uri", request::getRequestURI);
    this.addLazyKV("path", request::getPathInfo);
    this.addLazyKV("protocol", request::getProtocol);
    this.addLazyKV("remoteAddress", this::getRemoteAddress);
    this.addLazyKV("ipAddress", () -> this.get("remoteAddress"));
    this.addLazyKV("params", this::getParams);
    this.addLazyKV("headers", this::getHeaders);
    this.addLazyKV("body", this::getBody);
    this.addDynamicKVResolver((key) -> "data".equalsIgnoreCase(key) ? this.get("body") : null);
  }

  private String getRemoteAddress() {
    String[] headers = {
        "X-Real-IP",
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP"
    };

    for (String header : headers) {
      Object o = getHeaders().get(header.toLowerCase());
      List<String> values = o instanceof List ? (List<String>) o : Collections.singletonList((String) o);
      for (String value : values) {
        if (StringUtils.isNotEmpty(value) && !"unknown".equalsIgnoreCase(value)) {
          return value;
        }
      }
    }

    return request.getRemoteAddr();
  }

  private Map<String, Object> getParams() {
    return request.getParameterMap().entrySet().stream().map(e ->
        Tuple.of(e.getKey(), e.getValue().length == 1 ? e.getValue()[0] : e.getValue())
    ).filter(e ->
        !(e._2() instanceof String str) || !str.trim().isEmpty()
    ).collect(
        HashMap::new,
        (m, e) -> m.put(e._1, e._2),
        HashMap::putAll
    );
  }

  private Map<String, Object> getHeaders() {
    Map<String, Object> result = new HashMap<>(32);
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String name = headerNames.nextElement();
      Enumeration<String> headers = request.getHeaders(name);
      List<String> values = new ArrayList<>();
      while (headers.hasMoreElements()) {
        values.add(headers.nextElement());
      }

      result.put(name, values.size() == 1 ? values.get(0) : values);
    }

    return result;
  }

  private Map<String, Object> getBody() {
    String contentType = request.getHeader("Content-Type");
    if (StringUtils.isNotEmpty(contentType) && !contentType.contains("application/json")) {
      return Collections.emptyMap();
    }

    return Json.toMap(new String(request.getContentAsByteArray()));
  }
}