package com.ouroboros.dev.runtime.apisix;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class ApisixGatewayRealSmokeSupport {

  static void deleteRoute(RestTemplate restTemplate, String baseUrl, String adminKey, String routeId) {
    try {
      restTemplate.exchange(routeUrl(baseUrl, routeId), org.springframework.http.HttpMethod.DELETE, requestEntity(adminKey), String.class);
    } catch (RestClientException ignored) {
    }
  }

  static HttpEntity<String> requestEntity(String adminKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-API-KEY", adminKey);
    return new HttpEntity<String>("", headers);
  }

  static RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(2000);
    requestFactory.setReadTimeout(5000);
    return new RestTemplate(requestFactory);
  }

  private static String routeUrl(String baseUrl, String routeId) {
    return baseUrl + "/apisix/admin/routes/" + routeId;
  }
}
