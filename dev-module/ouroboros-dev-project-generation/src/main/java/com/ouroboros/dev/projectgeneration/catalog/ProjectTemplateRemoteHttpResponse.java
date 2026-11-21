package com.ouroboros.dev.projectgeneration.catalog;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProjectTemplateRemoteHttpResponse(
    int statusCode,
    byte[] body,
    List<InetAddress> resolvedAddresses,
    InetAddress peerAddress,
    Map<String, List<String>> headers
) {
  public ProjectTemplateRemoteHttpResponse {
    body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
    resolvedAddresses = resolvedAddresses == null
        ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(resolvedAddresses));
    headers = copyHeaders(headers);
  }

  @Override
  public byte[] body() {
    return Arrays.copyOf(body, body.length);
  }

  private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
    if (headers == null || headers.isEmpty()) {
      return Map.of();
    }
    Map<String, List<String>> copied = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      copied.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Collections.unmodifiableMap(copied);
  }
}
