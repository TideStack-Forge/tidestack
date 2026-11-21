package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkTokenCache;
import com.ouroboros.enterprise.dingtalk.DingtalkApplicationTokens;
import com.ouroboros.enterprise.EnterpriseDeadline;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;

import io.vavr.control.Try;

/**
 * 钉钉开放平台 HTTP 客户端生产实现（RestTemplate，遵循仓库 wechat 渠道模式）。
 *
 * <p>连接配置由统一的 {@code enterprise.connections} 快照提供；本客户端只读取解析后的
 * provider-specific 顶层字段和 typed credential，不直接访问配置中心。
 * token 换取只使用 credentialRef 解析出的 {@link DingtalkCredential}。
 */
public class DingtalkOpenApiHttpClient implements DingtalkOpenApiClient {

  private static final String DEFAULT_BASE_URL = "https://api.dingtalk.com";
  private static final String OAPI_BASE_URL = "https://oapi.dingtalk.com";
  /** 审批详情跳转地址前缀（厂商固定地址，corpId/instanceId/from 由调用侧拼接）。 */
  private static final String APPROVAL_DETAIL_URL_BASE =
      "https://applink.dingtalk.com/approval/detail?corpId=";
  static final long DEFAULT_OVERALL_TIMEOUT_MILLIS = 15_000L;

  private final ResolvedEnterpriseConnection connection;
  private final DingtalkCredential credential;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DingtalkApplicationTokens applicationTokens;
  private final long overallTimeoutMillis;

  public DingtalkOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      DingtalkCredential credential,
      RestTemplate restTemplate) {
    this(connection, credential, restTemplate, new DingtalkTokenCache(), DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  DingtalkOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      DingtalkCredential credential,
      RestTemplate restTemplate,
      DingtalkTokenCache tokenCache) {
    this(connection, credential, restTemplate, tokenCache, DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  DingtalkOpenApiHttpClient(
      ResolvedEnterpriseConnection connection,
      DingtalkCredential credential,
      RestTemplate restTemplate,
      DingtalkTokenCache tokenCache,
      long overallTimeoutMillis) {
    if (overallTimeoutMillis < 1L) {
      throw new IllegalArgumentException("钉钉 HTTP overall timeout 必须为正数");
    }
    this.connection = connection;
    this.credential = credential;
    this.restTemplate = restTemplate;
    this.applicationTokens = new DingtalkApplicationTokens(restTemplate, tokenCache);
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
    } catch (com.ouroboros.exception.ValidationException exception) {
      return Try.failure(new BusinessException("[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
          + "] 钉钉连接凭据不可用"));
    } catch (Exception exception) {
      return Try.failure(DingtalkFailureMapper.remote("token 换取", exception, false));
    }
  }

  private ResolvedEnterpriseConnection tokenConnection() {
    var config = new java.util.LinkedHashMap<String, Object>(connection == null ? Map.of() : connection.config());
    config.putIfAbsent("provider", "dingtalk");
    return new ResolvedEnterpriseConnection(connection == null ? "" : connection.connectionId(), config, credential);
  }

  @Override
  public Try<String> startProcessInstance(
      String originatorUserId,
      String processCode,
      List<Map<String, String>> formComponentValues) {
    return withToken((token, deadline) -> {
      try {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("originatorUserId", originatorUserId);
        body.put("processCode", processCode);
        body.put("deptId", -1);
        body.put("formComponentValues", formComponentValues);
        JsonNode response = objectMapper.readTree(deadline.execute(() -> restTemplate.postForObject(
            baseUrl() + "/v1.0/workflow/processInstances",
            httpEntityWithToken(body, token), String.class)));
        String instanceId = response.path("instanceId").asText(null);
        if (instanceId == null || instanceId.isBlank()) {
          return Try.failure(DingtalkFailureMapper.malformedResponse(
              "发起审批", new IllegalStateException("响应缺少 instanceId")));
        }
        return Try.success(instanceId);
      } catch (Exception ex) {
        return Try.failure(DingtalkFailureMapper.remote("发起审批", ex));
      }
    });
  }

  @Override
  public Try<ExternalFormSchema> getTemplateSchema(String processCode) {
    if ("THIRD_PARTY_ENTERPRISE".equals(applicationType())) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED
              + "] 钉钉第三方企业应用不支持组织内审批表单 schema 查询"));
    }
    return withToken((token, deadline) -> {
      try {
        JsonNode response = objectMapper.readTree(deadline.execute(() -> restTemplate.exchange(
            baseUrl() + "/v1.0/workflow/forms/schemas/processCodes?processCode={processCode}",
            org.springframework.http.HttpMethod.GET,
            httpEntityWithToken(null, token),
            String.class,
            processCode).getBody()));
        JsonNode result = response.path("result");
        JsonNode schema = result.path("schemaContent");
        return Try.success(new ExternalFormSchema(List.of(), Map.of("schema", schema.toString())));
      } catch (Exception ex) {
        return Try.failure(DingtalkFailureMapper.remote("表单 schema 查询", ex, false));
      }
    });
  }

  @Override
  public Try<ExternalProcessDefinition> validateTemplate(String processCode) {
    return getTemplateSchema(processCode).map(schema -> {
      String raw = String.valueOf(schema.raw().get("schema"));
      return new ExternalProcessDefinition(
          processCode, null, "钉钉审批模板", sha256Hex(raw));
    });
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstanceDetail(String processInstanceId) {
    return withToken((token, deadline) -> {
      try {
        Map<String, Object> body = Map.of("process_instance_id", processInstanceId);
        JsonNode response = objectMapper.readTree(deadline.execute(() -> restTemplate.postForObject(
            OAPI_BASE_URL + "/topapi/processinstance/get",
            httpEntityWithToken(body, token), String.class)));
        JsonNode instance = response.path("process_instance");
        String status = instance.path("status").asText("RUNNING");
        String result = instance.path("result").asText(null);
        String lifecycleStatus = "COMPLETED".equals(status) ? "FINISHED"
            : "CANCELED".equals(status) || "TERMINATED".equals(status) ? "CANCELED" : "RUNNING";
        String resultCode = "agree".equals(result) ? "APPROVED"
            : "refuse".equals(result) ? "REJECTED"
                : "CANCELED".equals(lifecycleStatus) ? "CANCELED" : null;
        String outcome = "APPROVED".equals(resultCode) ? "approved"
            : "REJECTED".equals(resultCode) ? "rejected"
                : "CANCELED".equals(resultCode) ? "cancelled" : null;
        return Try.success(new ExternalInstanceSnapshot(
            processInstanceId,
            lifecycleStatus,
            resultCode,
            outcome,
            appLinkUrl(processInstanceId),
            Map.of("dingtalkStatus", status, "result", result == null ? "" : result)));
      } catch (Exception ex) {
        return Try.failure(DingtalkFailureMapper.remote("实例详情查询", ex, false));
      }
    });
  }

  @Override
  public Try<List<ExternalTaskSnapshot>> listTasks(String processInstanceId) {
    return getInstanceDetail(processInstanceId).map(snapshot -> List.of());
  }

  @Override
  public Try<Void> executeTaskAction(
      String processInstanceId, String taskId, String action, String comment) {
    return withToken((token, deadline) -> {
      try {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("process_instance_id", processInstanceId);
        body.put("task_id", taskId);
        if (comment != null) {
          body.put("comment", comment);
        }
        String endpoint = "agree".equals(action)
            ? "/topapi/processinstance/agree" : "/topapi/processinstance/refuse";
        JsonNode response = objectMapper.readTree(deadline.execute(() -> restTemplate.postForObject(
            OAPI_BASE_URL + endpoint, httpEntityWithToken(body, token), String.class)));
        if (response.path("errcode").asInt(0) != 0) {
          return Try.failure(DingtalkFailureMapper.providerRejected(
              "任务动作", response.path("errcode").asText(null)));
        }
        return Try.success(null);
      } catch (Exception ex) {
        return Try.failure(DingtalkFailureMapper.remote("任务动作", ex));
      }
    });
  }

  private <T> Try<T> withToken(java.util.function.BiFunction<String, EnterpriseDeadline, Try<T>> function) {
    var deadline = EnterpriseDeadline.afterMillis(overallTimeoutMillis);
    Try<String> token = accessToken(deadline);
    return token.isFailure() ? Try.failure(token.getCause()) : function.apply(token.get(), deadline);
  }

  private org.springframework.http.HttpEntity<Object> httpEntityWithToken(
      Object body, String token) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.set("x-acs-dingtalk-access-token", token);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return new org.springframework.http.HttpEntity<>(body, headers);
  }

  private String applicationType() {
    return credential != null && credential.isThirdPartyEnterprise()
        ? "THIRD_PARTY_ENTERPRISE" : "INTERNAL";
  }

  private String baseUrl() {
    return DEFAULT_BASE_URL;
  }

  private String configField(String key) {
    if (connection == null) {
      return null;
    }
    Object direct = connection.config().get(key);
    return direct == null ? null : String.valueOf(direct);
  }

  private String requiredConfigField(String key) {
    String value = configField(key);
    if (value == null || value.isBlank()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 钉钉连接缺少 " + key);
    }
    return value;
  }

  private String appLinkUrl(String processInstanceId) {
    return APPROVAL_DETAIL_URL_BASE
        + requiredConfigField("corpId")
        + "&instanceId=" + processInstanceId + "&from=" + appLinkFrom();
  }

  private String appLinkFrom() {
    String from = credential.appKey() == null || credential.appKey().isBlank()
        ? credential.suiteKey() : credential.appKey();
    if (from == null || from.isBlank()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] 钉钉连接缺少 AppLink from 所需 appKey/suiteKey");
    }
    return from;
  }

  private String sha256Hex(String value) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

}
