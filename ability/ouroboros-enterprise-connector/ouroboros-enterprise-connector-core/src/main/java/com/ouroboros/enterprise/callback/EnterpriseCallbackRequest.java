package com.ouroboros.enterprise.callback;

import java.util.Map;

/** Raw transport input. Headers and query parameters remain separate trust boundaries. */
public record EnterpriseCallbackRequest(String method, Map<String, String> headers,
                                        Map<String, String> parameters, String body) {
  public EnterpriseCallbackRequest {
    headers = Map.copyOf(headers);
    parameters = Map.copyOf(parameters);
    body = body == null ? "" : body;
  }

  public String header(String name) {
    return headers.entrySet().stream().filter(entry -> name.equalsIgnoreCase(entry.getKey()))
        .map(Map.Entry::getValue).findFirst().orElse(null);
  }

  @Override public String toString() { return "EnterpriseCallbackRequest[method=" + method + ", material=****]"; }
}
