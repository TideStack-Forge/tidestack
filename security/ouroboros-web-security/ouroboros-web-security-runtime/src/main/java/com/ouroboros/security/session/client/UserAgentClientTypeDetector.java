package com.ouroboros.security.session.client;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;

@Priority(1)
public class UserAgentClientTypeDetector implements ClientTypeDetector {
  List<String> appKeywords = Arrays.asList("uni-app", "dart");
  List<String> webKeywords = Arrays.asList("Mozilla", "Chrome", "Safari", "Firefox", "MSIE");

  @Override
  public Optional<String> detectClientType(HttpServletRequest request) {
    String ua = request.getHeader("User-Agent");
    if (ua == null) {
      return Optional.empty();
    }
    if (appKeywords.stream().anyMatch(ua::contains)) {
      return Optional.of("app");
    }
    if (webKeywords.stream().anyMatch(ua::contains)) {
      return Optional.of("web");
    }
    return Optional.empty();
  }
}
