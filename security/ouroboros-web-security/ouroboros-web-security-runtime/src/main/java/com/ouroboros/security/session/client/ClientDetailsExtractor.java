package com.ouroboros.security.session.client;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientDetailsExtractor {
  Boolean supports(String clientType);

  Map<String, String> extractClientDetails(HttpServletRequest request);
}
