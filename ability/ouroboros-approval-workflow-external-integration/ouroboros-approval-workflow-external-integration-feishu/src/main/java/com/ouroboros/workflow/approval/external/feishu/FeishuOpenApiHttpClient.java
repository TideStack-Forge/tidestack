package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuTokenCache;
import com.ouroboros.enterprise.feishu.FeishuApplicationTokens;
import com.ouroboros.enterprise.EnterpriseDeadline;

import com.ouroboros.enterprise.feishu.FeishuCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryResult;
import com.ouroboros.workflow.approval.external.ExternalStartResult;

import io.vavr.control.Try;

/** Production HTTP implementation for Feishu native approval APIs. */
public class FeishuOpenApiHttpClient implements FeishuOpenApiClient {

  private static final String DEFAULT_BASE_URL = "https://open.feishu.cn";
  static final long DEFAULT_OVERALL_TIMEOUT_MILLIS = 15_000L;

  private final ResolvedEnterpriseConnection connection;
  private final FeishuCredential credential;
  private final RestTemplate restTemplate;
  private final FeishuApplicationTokens applicationTokens;
  private final long overallTimeoutMillis;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FeishuOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      FeishuCredential credential,
      RestTemplate restTemplate) {
    this(connection, credential, restTemplate, new FeishuTokenCache(), DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  FeishuOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      FeishuCredential credential,
      RestTemplate restTemplate,
      FeishuTokenCache tokenCache,
      long overallTimeoutMillis) {
    if (overallTimeoutMillis < 1L) {
      throw new IllegalArgumentException("飞书 HTTP overall timeout 必须为正数");
    }
    this.connection = connection;
    this.credential = credential;
    this.restTemplate = restTemplate;
    this.applicationTokens = new FeishuApplicationTokens(restTemplate, tokenCache);
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
      return Try.failure(FeishuFailureMapper.remote("tenant token 换取", exception, false));
    }
  }

  private ResolvedEnterpriseConnection tokenConnection() {
    var config = new java.util.LinkedHashMap<String, Object>(connection == null ? Map.of() : connection.config());
    config.putIfAbsent("provider", "feishu");
    return new ResolvedEnterpriseConnection(connection == null ? "" : connection.connectionId(), config, credential);
  }

  @Override
  public Try<ExternalProcessDefinition> validateApproval(String approvalCode) {
    return getApprovalForm(approvalCode).map(schema -> new ExternalProcessDefinition(
        approvalCode,
        stringValue(schema.raw().get("versionId")),
        stringValue(schema.raw().getOrDefault("approvalName", "飞书审批定义")),
        sha256Hex(String.valueOf(schema.raw().get("schema")))));
  }

  @Override
  public Try<ExternalFormSchema> getApprovalForm(String approvalCode) {
    return withToken((token, deadline) -> {
      try {
        String url = baseUrl() + "/open-apis/approval/v4/approvals/{approvalCode}";
        JsonNode response = readJson(deadline.execute(() -> restTemplate.exchange(
            url, HttpMethod.GET, authorizedEntity(null, token), String.class, approvalCode).getBody()),
            "审批定义查询");
        requireSuccess(response, "审批定义查询");
        JsonNode data = response.path("data");
        JsonNode schemaNode = formSchemaNode(data.path("form"));
        if (!schemaNode.isArray()) {
          throw FeishuFailureMapper.malformedResponse(
              "审批定义查询", new IllegalStateException("响应 form 不是控件数组"));
        }
        List<Map<String, Object>> components = new ArrayList<>();
        for (JsonNode component : schemaNode) {
          components.add(objectMapper.convertValue(
              component,
              objectMapper.getTypeFactory().constructMapType(
                  LinkedHashMap.class, String.class, Object.class)));
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("approvalName", data.path("approval_name").asText("飞书审批定义"));
        raw.put("versionId", nullIfBlank(data.path("version_id").asText(null)));
        raw.put("schema", schemaNode.toString());
        return Try.success(new ExternalFormSchema(List.copyOf(components), raw));
      } catch (Exception ex) {
        return Try.failure(FeishuFailureMapper.remote("审批定义查询", ex, false));
      }
    });
  }

  @Override
  public Try<String> createInstance(
      String approvalCode,
      String externalUserId,
      String externalDepartmentId,
      String formJson,
      String uuid) {
    return withToken((token, deadline) -> {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approval_code", approvalCode);
        body.put(startUserIdField(), externalUserId);
        if (externalDepartmentId != null && !externalDepartmentId.isBlank()) {
          body.put("department_id", externalDepartmentId);
        }
        body.put("form", formJson);
        body.put("uuid", uuid);
        String url = baseUrl() + "/open-apis/approval/v4/instances";
        JsonNode response = readJson(deadline.execute(() -> restTemplate.postForObject(
            url, authorizedEntity(body, token), String.class)), "创建审批实例");
        if (response.path("code").asInt(0) == 60012) {
          Try<ExternalInstanceSnapshot> existing = getInstance(uuid);
          return existing.isSuccess()
              ? Try.success(existing.get().externalInstanceId())
              : Try.failure(existing.getCause());
        }
        requireSuccess(response, "创建审批实例");
        return Try.success(requiredText(response.path("data"), "instance_code", "创建审批实例"));
      } catch (Exception ex) {
        return Try.failure(FeishuFailureMapper.remote("创建审批实例", ex, true));
      }
    });
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstance(String instanceIdentity) {
    return withToken((token, deadline) -> {
      try {
        String url = baseUrl()
            + "/open-apis/approval/v4/instances/{instanceIdentity}?user_id_type="
            + requiredUserIdType();
        JsonNode response = readJson(deadline.execute(() -> restTemplate.exchange(
            url,
            HttpMethod.GET,
            authorizedEntity(null, token),
            String.class,
            instanceIdentity).getBody()), "审批实例详情查询");
        requireSuccess(response, "审批实例详情查询");
        JsonNode data = response.path("data");
        String instanceCode = data.path("instance_code").asText(instanceIdentity);
        String status = data.path("status").asText(null);
        if (status == null || status.isBlank()) {
          throw FeishuFailureMapper.malformedResponse(
              "审批实例详情查询", new IllegalStateException("响应缺少 status"));
        }
        if (!List.of("PENDING", "APPROVED", "REJECTED", "CANCELED", "DELETED",
            "REVERTED", "OVERTIME_CLOSE", "OVERTIME_RECOVER").contains(status)) {
          throw FeishuFailureMapper.malformedResponse(
              "审批实例详情查询",
              new IllegalStateException("未知 status=" + status));
        }
        String lifecycle = switch (status) {
          case "PENDING", "OVERTIME_RECOVER" -> "RUNNING";
          case "APPROVED", "REJECTED" -> "FINISHED";
          case "CANCELED", "DELETED", "REVERTED", "OVERTIME_CLOSE" -> "CANCELED";
          default -> throw new IllegalStateException("未覆盖的飞书实例状态: " + status);
        };
        String resultCode = switch (status) {
          case "PENDING", "OVERTIME_RECOVER" -> null;
          case "APPROVED" -> "APPROVED";
          case "REJECTED" -> "REJECTED";
          case "CANCELED" -> "CANCELED";
          case "DELETED" -> "FEISHU_DELETED";
          case "REVERTED" -> "FEISHU_REVERTED";
          case "OVERTIME_CLOSE" -> "FEISHU_OVERTIME_CLOSE";
          default -> throw new IllegalStateException("未覆盖的飞书实例状态: " + status);
        };
        String outcome = switch (status) {
          case "APPROVED" -> "approved";
          case "REJECTED" -> "rejected";
          case "CANCELED" -> "cancelled";
          default -> null;
        };
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("feishuStatus", status);
        putIfText(raw, "uuid", data.path("uuid").asText(null));
        putIfText(raw, "approvalCode", data.path("approval_code").asText(null));
        return Try.success(new ExternalInstanceSnapshot(
            instanceCode, lifecycle, resultCode, outcome, null, Map.copyOf(raw)));
      } catch (Exception ex) {
        return Try.failure(FeishuFailureMapper.remote("审批实例详情查询", ex, false));
      }
    });
  }

  @Override
  public Try<ExternalStartRecoveryResult> recoverStart(String uuid) {
    Try<ExternalInstanceSnapshot> instance = getInstance(uuid);
    if (instance.isFailure()) {
      return Try.success(ExternalStartRecoveryResult.unknown(
          "飞书按 uuid 查询未能确认远端实例，不自动重建"));
    }
    ExternalInstanceSnapshot snapshot = instance.get();
    return Try.success(ExternalStartRecoveryResult.found(new ExternalStartResult(
        snapshot.externalInstanceId(), snapshot.externalUrl(), snapshot)));
  }

  private <T> Try<T> withToken(java.util.function.BiFunction<String, EnterpriseDeadline, Try<T>> function) {
    var deadline = EnterpriseDeadline.afterMillis(overallTimeoutMillis);
    Try<String> token = accessToken(deadline);
    return token.isFailure() ? Try.failure(token.getCause()) : function.apply(token.get(), deadline);
  }

  private JsonNode formSchemaNode(JsonNode form) {
    if (form.isTextual()) {
      try {
        return objectMapper.readTree(form.asText());
      } catch (Exception ex) {
        throw FeishuFailureMapper.malformedResponse("审批定义查询", ex);
      }
    }
    return form;
  }

  private JsonNode readJson(String body, String operation) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception ex) {
      throw FeishuFailureMapper.malformedResponse(operation, ex);
    }
  }

  private void requireSuccess(JsonNode response, String operation) {
    int code = response.path("code").asInt(0);
    if (code != 0) {
      throw FeishuFailureMapper.providerRejected(operation, String.valueOf(code));
    }
  }

  private String requiredText(JsonNode node, String field, String operation) {
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw FeishuFailureMapper.malformedResponse(
          operation, new IllegalStateException("响应缺少 " + field));
    }
    return value;
  }

  private HttpEntity<Object> jsonEntity(Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private HttpEntity<Object> authorizedEntity(Object body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private String requiredUserIdType() {
    return "open_id";
  }

  private String startUserIdField() {
    return "open_id";
  }

  private String baseUrl() {
    return DEFAULT_BASE_URL;
  }

  private BusinessException connectionUnavailable(String reason) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE + "] 飞书 " + reason);
  }

  private void putIfText(Map<String, Object> target, String key, String value) {
    if (value != null && !value.isBlank()) {
      target.put(key, value);
    }
  }

  private String nullIfBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
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
