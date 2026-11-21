package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomTokenCache;
import com.ouroboros.enterprise.wecom.WecomApplicationTokens;
import com.ouroboros.enterprise.EnterpriseDeadline;

import com.ouroboros.enterprise.wecom.WecomCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;

import io.vavr.control.Try;

/** Production HTTP implementation for the Enterprise WeChat third-party approval APIs. */
public class WecomOpenApiHttpClient implements WecomOpenApiClient {

  private static final String DEFAULT_BASE_URL = "https://qyapi.weixin.qq.com";
  static final long DEFAULT_OVERALL_TIMEOUT_MILLIS = 15_000L;

  private final ResolvedEnterpriseConnection connection;
  private final WecomCredential credential;
  private final RestTemplate restTemplate;
  private final WecomApplicationTokens applicationTokens;
  private final long overallTimeoutMillis;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public WecomOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      WecomCredential credential,
      RestTemplate restTemplate) {
    this(connection, credential, restTemplate, new WecomTokenCache(), DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  WecomOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      WecomCredential credential,
      RestTemplate restTemplate,
      WecomTokenCache tokenCache,
      long overallTimeoutMillis) {
    if (overallTimeoutMillis < 1L) {
      throw new IllegalArgumentException("企业微信 HTTP overall timeout 必须为正数");
    }
    this.connection = connection;
    this.credential = credential;
    this.restTemplate = restTemplate;
    this.applicationTokens = new WecomApplicationTokens(restTemplate, tokenCache);
    this.overallTimeoutMillis = overallTimeoutMillis;
  }

  @Override
  public Try<String> accessToken() {
    return accessToken(EnterpriseDeadline.afterMillis(overallTimeoutMillis));
  }

  private Try<String> accessToken(EnterpriseDeadline deadline) {
    if (credential == null) {
      return Try.failure(new BusinessException("企业连接缺少 credentialRef 凭据"));
    }
    try {
      return Try.success(applicationTokens.accessToken(tokenConnection(), deadline));
    } catch (Exception exception) {
      return Try.failure(WecomFailureMapper.remote("access token 换取", exception, false));
    }
  }

  private ResolvedEnterpriseConnection tokenConnection() {
    var config = new java.util.LinkedHashMap<String, Object>(connection == null ? Map.of() : connection.config());
    config.putIfAbsent("provider", "wecom");
    return new ResolvedEnterpriseConnection(connection == null ? "" : connection.connectionId(), config, credential);
  }

  @Override
  public Try<ExternalProcessDefinition> validateTemplate(String templateId) {
    return getTemplateSchema(templateId).map(schema -> new ExternalProcessDefinition(
        templateId,
        null,
        String.valueOf(schema.raw().getOrDefault("templateName", "企业微信审批模板")),
        sha256Hex(String.valueOf(schema.raw().get("schema")))));
  }

  @Override
  public Try<ExternalFormSchema> getTemplateSchema(String templateId) {
    return withToken((token, deadline) -> {
      try {
        JsonNode response = postWithToken(
            "/cgi-bin/oa/gettemplatedetail", token, Map.of("template_id", templateId), false, deadline);
        JsonNode content = response.path("template_content");
        List<Map<String, Object>> components = new ArrayList<>();
        if (content.path("controls").isArray()) {
          for (JsonNode control : content.path("controls")) {
            components.add(objectMapper.convertValue(
                control,
                objectMapper.getTypeFactory().constructMapType(
                    LinkedHashMap.class, String.class, Object.class)));
          }
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("templateName", localizedText(response.path("template_names"), "企业微信审批模板"));
        raw.put("schema", content.toString());
        return Try.success(new ExternalFormSchema(List.copyOf(components), Map.copyOf(raw)));
      } catch (Exception ex) {
        return Try.failure(WecomFailureMapper.remote("审批模板详情查询", ex, false));
      }
    });
  }

  @Override
  public Try<String> startApproval(
      String creatorUserId,
      String templateId,
      List<Map<String, Object>> contents,
      String summary) {
    return withToken((token, deadline) -> {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("creator_userid", creatorUserId);
        body.put("template_id", templateId);
        body.put("use_template_approver", 1);
        body.put("apply_data", Map.of("contents", contents));
        body.put("summary_list", List.of(Map.of(
            "summary_info", List.of(Map.of(
                "text", summaryText(summary),
                "lang", "zh_CN")))));
        JsonNode response = postWithToken("/cgi-bin/oa/applyevent", token, body, true, deadline);
        return Try.success(requiredText(response, "sp_no", "提交审批申请"));
      } catch (Exception ex) {
        return Try.failure(WecomFailureMapper.remote("提交审批申请", ex, true));
      }
    });
  }

  @Override
  public Try<ExternalInstanceSnapshot> getApprovalDetail(String approvalNumber) {
    return withToken((token, deadline) -> {
      try {
        JsonNode response = postWithToken(
            "/cgi-bin/oa/getapprovaldetail", token, Map.of("sp_no", approvalNumber), false, deadline);
        JsonNode info = response.path("info");
        int status = info.path("sp_status").asInt(-1);
        if (!List.of(1, 2, 3, 4, 6, 7, 10).contains(status)) {
          throw WecomFailureMapper.malformedResponse(
              "审批申请详情查询",
              new IllegalStateException("未知 sp_status=" + status));
        }
        String lifecycle = switch (status) {
          case 1 -> "RUNNING";
          case 2, 3, 10 -> "FINISHED";
          case 4, 6, 7 -> "CANCELED";
          default -> "RUNNING";
        };
        String resultCode = switch (status) {
          case 1 -> null;
          case 2 -> "APPROVED";
          case 3 -> "REJECTED";
          case 4 -> "CANCELED";
          case 6 -> "WECOM_REVOKED_AFTER_APPROVAL";
          case 7 -> "WECOM_DELETED";
          case 10 -> "WECOM_PAID";
          default -> "WECOM_STATUS_" + status;
        };
        String outcome = switch (status) {
          case 2 -> "approved";
          case 3 -> "rejected";
          case 4 -> "cancelled";
          default -> null;
        };
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("wecomStatus", status);
        if (!info.path("template_id").isMissingNode()) {
          raw.put("templateId", info.path("template_id").asText());
        }
        return Try.success(new ExternalInstanceSnapshot(
            approvalNumber, lifecycle, resultCode, outcome, null, Map.copyOf(raw)));
      } catch (Exception ex) {
        return Try.failure(WecomFailureMapper.remote("审批申请详情查询", ex, false));
      }
    });
  }

  private JsonNode postWithToken(
      String path, String token, Object body, boolean sideEffect, EnterpriseDeadline deadline) throws Exception {
    String url = UriComponentsBuilder.fromUriString(baseUrl())
        .path(path)
        .queryParam("access_token", token)
        .build().toUriString();
    JsonNode response = readJson(deadline.execute(
        () -> restTemplate.postForObject(url, jsonEntity(body), String.class)), path);
    try {
      requireSuccess(response, path);
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw WecomFailureMapper.remote(path, ex, sideEffect);
    }
    return response;
  }

  private <T> Try<T> withToken(java.util.function.BiFunction<String, EnterpriseDeadline, Try<T>> function) {
    var deadline = EnterpriseDeadline.afterMillis(overallTimeoutMillis);
    Try<String> token = accessToken(deadline);
    return token.isFailure() ? Try.failure(token.getCause()) : function.apply(token.get(), deadline);
  }

  private JsonNode readJson(String body, String operation) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception ex) {
      throw WecomFailureMapper.malformedResponse(operation, ex);
    }
  }

  private void requireSuccess(JsonNode response, String operation) {
    int code = response.path("errcode").asInt(0);
    if (code != 0) {
      throw WecomFailureMapper.providerRejected(operation, String.valueOf(code));
    }
  }

  private String requiredText(JsonNode response, String field, String operation) {
    String value = response.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw WecomFailureMapper.malformedResponse(
          operation, new IllegalStateException("响应缺少 " + field));
    }
    return value;
  }

  private HttpEntity<Object> jsonEntity(Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private String localizedText(JsonNode names, String fallback) {
    if (names.isArray()) {
      for (JsonNode name : names) {
        String text = name.path("text").asText(null);
        if (text != null && !text.isBlank()) {
          return text;
        }
      }
    }
    return fallback;
  }

  private String summaryText(String summary) {
    String value = summary == null || summary.isBlank() ? "企业微信审批申请" : summary;
    int codePoints = value.codePointCount(0, value.length());
    if (codePoints <= 20) {
      return value;
    }
    int end = value.offsetByCodePoints(0, 20);
    return value.substring(0, end);
  }

  private String requiredCorpId() {
    String corpId = configField("corpId");
    if (corpId == null || corpId.isBlank()) {
      throw connectionUnavailable("连接缺少 corpId");
    }
    return corpId;
  }

  private String configField(String key) {
    Object direct = connection == null ? null : connection.config().get(key);
    return direct == null ? null : String.valueOf(direct);
  }

  private String baseUrl() {
    return DEFAULT_BASE_URL;
  }

  private BusinessException connectionUnavailable(String reason) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 企业微信 " + reason);
  }

  private String sha256Hex(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        hex.append(String.format("%02x", item));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }
}
