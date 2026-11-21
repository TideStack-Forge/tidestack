package com.ouroboros.businesslog.util;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.ouroboros.util.Json;

// 它不需要Lazy，因为必然会被调用
public class LazyResponseMap extends LazyMap {

  public LazyResponseMap(ContentCachingResponseWrapper response) {
    super();
    Map<String, Object> responseBody = getResponseBody(response);
    this.addLazyKV("body", () -> responseBody);
    this.addLazyKV("data", () -> Optional.ofNullable(responseBody.get("data"))
        .orElseGet(Collections::emptyMap));
  }

  private static Map<String, Object> getResponseBody(ContentCachingResponseWrapper response) {
    String contentType = response.getHeader("Content-Type");
    if (StringUtils.isNotEmpty(contentType) && !contentType.contains("application/json")) {
      return Collections.emptyMap();
    }
    return Json.toMap(new String(response.getContentAsByteArray()));
  }
}
