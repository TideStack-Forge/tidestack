package com.ouroboros.upload.controller;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

public class UploadRequestContext implements Map<String, String> {
  HttpServletRequest request;

  public UploadRequestContext(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  // 驼峰转连字符
  private String getNormalizedHeaderKey(String key) {
    return key.replaceAll("([A-Z])", "-$1");
  }

  @Override
  public boolean containsKey(Object key) {
    if (!(key instanceof String keyString)) {
      return false;
    }
    return get(keyString) != null;
  }

  @Override
  public boolean containsValue(Object value) {
    return false;
  }

  @Override
  public String get(Object key) {
    if (!(key instanceof String keyString)) {
      return null;
    }

    // attribute优先级最高
    if (request.getAttribute(keyString) instanceof String attrValue) {
      return attrValue;
    }

    String value;

    // 其次是queryString和post body
    value = request.getParameter(keyString);
    if (value != null) {
      return value;
    }

    // 然后是各种不同格式的头
    value = request.getHeader(keyString);
    if (value != null) {
      return value;
    }

    var headerKeyStr = getNormalizedHeaderKey(keyString);
    value = request.getHeader(headerKeyStr);
    if (value != null) {
      return value;
    }

    return Optional.ofNullable(request.getHeader("X-" + headerKeyStr))
        .orElseGet(() -> request.getHeader("x-" + keyString));
  }

  @Override
  public String put(String key, String value) {
    return null;
  }

  @Override
  public String remove(Object key) {
    return null;
  }

  @Override
  public void putAll(Map<? extends String, ? extends String> m) {

  }

  @Override
  public void clear() {

  }

  @Override
  public Set<String> keySet() {
    return null;
  }

  @Override
  public Collection<String> values() {
    return null;
  }

  @Override
  public Set<Entry<String, String>> entrySet() {
    return null;
  }
}
