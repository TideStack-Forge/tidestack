package com.ouroboros.security.session.client;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientTypeDetector {
  Optional<String> detectClientType(HttpServletRequest request);
}
