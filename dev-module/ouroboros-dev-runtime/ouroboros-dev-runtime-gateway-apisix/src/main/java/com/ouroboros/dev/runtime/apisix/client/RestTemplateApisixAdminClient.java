package com.ouroboros.dev.runtime.apisix.client;

import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;

public class RestTemplateApisixAdminClient implements ApisixAdminClient {
  private final ApisixGatewayProperties properties;
  private final RestTemplate restTemplate;

  public RestTemplateApisixAdminClient(ApisixGatewayProperties properties) {
    this.properties = properties;
    this.restTemplate = new RestTemplate(requestFactory(properties));
  }

  @Override
  public Try<Void> upsertRoute(String routeId, ApisixRoutePayload routePayload) {
    try {
      restTemplate.exchange(routeUrl(routeId), HttpMethod.PUT, jsonEntity(routePayload), String.class);
      return Try.success(null);
    } catch (HttpStatusCodeException ex) {
      return Try.failure(ex);
    }
  }

  @Override
  public Try<Void> deleteRoute(String routeId) {
    try {
      restTemplate.exchange(routeUrl(routeId), HttpMethod.DELETE, jsonEntity(null), String.class);
      return Try.success(null);
    } catch (HttpStatusCodeException ex) {
      int statusCode = ex.getStatusCode().value();
      return statusCode == 404 ? Try.success(null) : Try.failure(ex);
    }
  }

  private SimpleClientHttpRequestFactory requestFactory(ApisixGatewayProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis() == null ? 2000 : properties.getConnectTimeoutMillis().intValue());
    requestFactory.setReadTimeout(properties.getReadTimeoutMillis() == null ? 5000 : properties.getReadTimeoutMillis().intValue());
    return requestFactory;
  }

  private String routeUrl(String routeId) {
    return properties.getBaseUrl() + "/apisix/admin/routes/" + routeId;
  }

  private HttpEntity<String> jsonEntity(ApisixRoutePayload routePayload) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-API-KEY", properties.getAdminKey());
    return new HttpEntity<String>(routePayload == null ? "" : toJson(routePayload), headers);
  }

  private String toJson(ApisixRoutePayload routePayload) {
    StringBuilder builder = new StringBuilder();
    builder.append("{");
    builder.append("\"uri\":\"").append(routePayload.getUri()).append("\",");
    builder.append("\"hosts\":[");
    for (int i = 0; i < routePayload.getHosts().size(); i++) {
      if (i > 0) {
        builder.append(",");
      }
      builder.append("\"").append(routePayload.getHosts().get(i)).append("\"");
    }
    builder.append("],");
    builder.append("\"upstream\":{");
    builder.append("\"type\":\"").append(routePayload.getUpstream().getType()).append("\",");
    builder.append("\"nodes\":{");
    Iterator<Map.Entry<String, Integer>> iterator = routePayload.getUpstream().getNodes().entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Integer> entry = iterator.next();
      builder.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
      if (iterator.hasNext()) {
        builder.append(",");
      }
    }
    builder.append("}}");
    builder.append("}");
    return builder.toString();
  }
}
