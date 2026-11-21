package com.ouroboros.security.session.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ouroboros.util.SPI;

// 因为用途单一，先不挂到filter上，假装是个Holder，以后扩大使用范围的时候再调整实现
public class ClientDetailsHolder {
  private static List<ClientDetailsExtractor> extractors = SPI.getSortedServiceStream(ClientDetailsExtractor.class)
      .collect(Collectors.toList());

  public static Map<String, String> getClientDetails() {
    HttpServletRequest request = getCurrentRequest();
    String clientType = ClientTypeHolder.getClientType();
    return extractors.stream()
        .filter(e -> e.supports(clientType))
        .map(e -> e.extractClientDetails(request))
        .collect(LinkedHashMap::new, Map::putAll, Map::putAll);
  }

  private static HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      return attributes.getRequest();
    }
    return null;
  }
}
