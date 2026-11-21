package com.ouroboros.dev.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Credentials stay in the IDE host; browser content cannot choose another application. */
public final class LocalEditorAccessFilter extends OncePerRequestFilter {
  private final byte[] authorization;
  private final byte[] developmentKey;
  private final String appName;
  private final LocalHelperConnection connection;
  public LocalEditorAccessFilter(String token, String appName, LocalHelperConnection connection) {
    if (token == null || token.isBlank() || !appName.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Editor token and valid app name are required");
    authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    developmentKey = token.getBytes(StandardCharsets.UTF_8);
    this.appName = appName; this.connection = connection;
  }
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String bearer = request.getHeader("Authorization");
    String devKey = request.getHeader("X-TideStack-Dev-Key");
    boolean valid = bearer != null && devKey == null
        && java.util.Collections.list(request.getHeaders("Authorization")).size() == 1
        && MessageDigest.isEqual(authorization, bearer.getBytes(StandardCharsets.UTF_8))
        || bearer == null && devKey != null
        && java.util.Collections.list(request.getHeaders("X-TideStack-Dev-Key")).size() == 1
        && MessageDigest.isEqual(developmentKey, devKey.getBytes(StandardCharsets.UTF_8));
    if (!valid) {
      response.sendError(401, "Valid development key or editor access token required"); return;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!(path.startsWith("/api/dev/" + appName + "/") || path.startsWith("/api/app/" + appName + "/field-types/"))) {
      response.sendError(404); return;
    }
    try { connection.ensureReady(); }
    catch (RuntimeException error) {
      logger.warn("Local editor helper is unavailable", error);
      response.sendError(503, "Local helper is unavailable; check application and helper configuration"); return;
    }
    chain.doFilter(request, response);
  }
}
