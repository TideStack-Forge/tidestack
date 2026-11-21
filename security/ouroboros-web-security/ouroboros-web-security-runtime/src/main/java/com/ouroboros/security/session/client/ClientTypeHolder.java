package com.ouroboros.security.session.client;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ouroboros.util.SPI;

// 因为目前只有登录时用到，先不挂到filter上，假装是个Holder，以后扩大使用范围的时候再调整实现
public class ClientTypeHolder {
  private static final String DEFAULT_CLIENT_TYPE = "UNKNOWN";
  private static List<ClientTypeDetector> clientTypeDetectors = SPI.getSortedServiceStream(ClientTypeDetector.class)
      .collect(Collectors.toList());

  public static String getClientType() {
    HttpServletRequest request = getCurrentRequest();
    if (request == null) {
      return DEFAULT_CLIENT_TYPE;
    }
    return clientTypeDetectors.stream()
        .map(d -> d.detectClientType(request))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElse(DEFAULT_CLIENT_TYPE);
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
