package com.ouroboros.security.session.client;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public class UserAgentDetailsExtractor implements ClientDetailsExtractor {
  @Override
  public Boolean supports(String clientType) {
    return "web".equalsIgnoreCase(clientType);
  }

  @Override
  public Map<String, String> extractClientDetails(HttpServletRequest request) {
    String ua = request.getHeader("User-Agent");
    Map<String, String> clientDetails = new HashMap<>();
    if (ua != null) {
      clientDetails.put("User-Agent", ua);
      clientDetails.put("os", extractOsFromUserAgent(ua));
      clientDetails.put("browser", extractBrowserFromUserAgent(ua));
    }
    return clientDetails;
  }

  private String extractOsFromUserAgent(String ua) {
    if (ua == null || ua.isEmpty()) {
      return "unknown";
    }
    String lowerUa = ua.toLowerCase();
    if (lowerUa.contains("windows nt 10.0")) {
      return "Windows 10";
    } else if (lowerUa.contains("windows nt 6.3")) {
      return "Windows 8.1";
    } else if (lowerUa.contains("windows nt 6.2")) {
      return "Windows 8";
    } else if (lowerUa.contains("windows nt 6.1")) {
      return "Windows 7";
    } else if (lowerUa.contains("windows")) {
      return "Windows";
    } else if (lowerUa.contains("mac os x")) {
      return "macOS";
    } else if (lowerUa.contains("android")) {
      return "Android";
    } else if (lowerUa.contains("iphone") || lowerUa.contains("ipad")) {
      return "iOS";
    } else if (lowerUa.contains("linux")) {
      return "Linux";
    }
    return "unknown";
  }

  private String extractBrowserFromUserAgent(String ua) {
    if (ua == null || ua.isEmpty()) {
      return "unknown";
    }
    String lowerUa = ua.toLowerCase();
    if (lowerUa.contains("edg/")) {
      return "Edge";
    } else if (lowerUa.contains("chrome/") && !lowerUa.contains("edg/")) {
      return "Chrome";
    } else if (lowerUa.contains("firefox/")) {
      return "Firefox";
    } else if (lowerUa.contains("safari/") && !lowerUa.contains("chrome/")) {
      return "Safari";
    } else if (lowerUa.contains("opera/") || lowerUa.contains("opr/")) {
      return "Opera";
    } else if (lowerUa.contains("msie") || lowerUa.contains("trident/")) {
      return "Internet Explorer";
    }
    return "unknown";
  }
}
