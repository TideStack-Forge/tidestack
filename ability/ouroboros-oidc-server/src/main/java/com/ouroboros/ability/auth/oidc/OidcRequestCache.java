package com.ouroboros.ability.auth.oidc;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.server.ResponseStatusException;

/** Bounded original-request slots in the existing protocol HttpSession. */
public final class OidcRequestCache implements RequestCache {
  private static final String SLOTS = OidcRequestCache.class.getName();
  public static final String REQUEST_ID = "loginRequestId";
  private record Slot(DefaultSavedRequest request, Instant expiresAt) implements Serializable { }

  @Override
  public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
    if (!request.getMethod().equals("GET") || !request.getRequestURI().substring(request.getContextPath().length()).equals("/oauth2/authorize")) { return; }
    var session = request.getSession();
    synchronized (session) {
      Map<String, Slot> slots = slots(request);
      if (slots.size() >= 8) { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "待登录授权请求过多"); }
      String id = UUID.randomUUID().toString();
      slots.put(id, new Slot(new DefaultSavedRequest(request, new org.springframework.security.web.PortResolverImpl()),
          Instant.now().plusSeconds(300)));
      session.setAttribute(SLOTS, slots);
      request.setAttribute(REQUEST_ID, id);
    }
  }

  @Override
  public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    if (session == null) { return null; }
    synchronized (session) {
      var slots = slots(request);
      String id = id(request, slots);
      return id == null ? null : slots.get(id).request();
    }
  }

  @Override
  public void removeRequest(HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    if (session == null) { return; }
    synchronized (session) {
      var slots = slots(request);
      String id = id(request, slots);
      if (id != null) { slots.remove(id); }
      session.setAttribute(SLOTS, slots);
    }
  }

  @Override
  public jakarta.servlet.http.HttpServletRequest getMatchingRequest(HttpServletRequest request, HttpServletResponse response) { return null; }

  private String id(HttpServletRequest request, Map<String, Slot> slots) {
    String[] ids = request.getParameterValues(REQUEST_ID);
    if (ids != null) { return ids.length == 1 && slots.containsKey(ids[0]) ? ids[0] : null; }
    return slots.size() == 1 ? slots.keySet().iterator().next() : null;
  }

  private Map<String, Slot> slots(HttpServletRequest request) {
    var result = new LinkedHashMap<String, Slot>();
    Object stored = request.getSession().getAttribute(SLOTS);
    if (stored instanceof Map<?, ?> map) {
      map.forEach((key, value) -> {
        if (key instanceof String id && value instanceof Slot slot && slot.expiresAt().isAfter(Instant.now())) {
          result.put(id, slot);
        }
      });
    }
    return result;
  }
}
