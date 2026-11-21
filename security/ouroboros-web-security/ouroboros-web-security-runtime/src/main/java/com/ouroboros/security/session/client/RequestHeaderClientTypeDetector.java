package com.ouroboros.security.session.client;

import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;

@Priority(10)
public class RequestHeaderClientTypeDetector implements ClientTypeDetector {
  String HEADER_NAME = "X-Client-Type";

  @Override
  public Optional<String> detectClientType(HttpServletRequest request) {
    return Optional.ofNullable(request.getHeader(HEADER_NAME));
  }
}
