package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomTokenCache;

import com.ouroboros.enterprise.wecom.WecomCredential;

import java.util.Map;
import java.util.Set;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;

/** Provider for Enterprise WeChat third-party approval as an external authority. */
public class WecomEngineAdapterProvider implements ApprovalWorkflowEngineAdapterProvider {

  public static final String ADAPTER_ID = "wecom-approval-third-party";

  private final ExternalUserIdentityMapper identityMapper;
  private final org.springframework.web.client.RestTemplate restTemplate;
  private final WecomTokenCache tokenCache;
  private final long overallTimeoutMillis;

  public WecomEngineAdapterProvider() {
    this(null, spiRestTemplate(), new WecomTokenCache(),
        WecomOpenApiHttpClient.DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  WecomEngineAdapterProvider(
      ExternalUserIdentityMapper identityMapper,
      org.springframework.web.client.RestTemplate restTemplate,
      WecomTokenCache tokenCache,
      long overallTimeoutMillis) {
    this.identityMapper = identityMapper;
    this.restTemplate = restTemplate;
    this.tokenCache = tokenCache;
    this.overallTimeoutMillis = overallTimeoutMillis;
  }

  /**
   * SPI/测试场景的默认 HTTP 客户端：必须带超时。无超时的 RestTemplate 会让一次外部调用
   * 无限期占用线程；Spring 装配路径另有带超时的 bean（见 AutoConfiguration）。
   */
  private static org.springframework.web.client.RestTemplate spiRestTemplate() {
    org.springframework.http.client.SimpleClientHttpRequestFactory factory =
        new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    return new org.springframework.web.client.RestTemplate(factory);
  }

  @Override
  public String provider() {
    return "wecom";
  }







  @Override
  public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
    return new ApprovalWorkflowEngineAdapterDescriptor(
        ADAPTER_ID,
        "企业微信第三方应用审批",
        "1.0",
        Set.of(),
        Set.of(Capability.EXTERNAL_FINAL_RESULT),
        Set.of());
  }

  @Override
  public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
    WecomCredential credential = credential(connection);
    return new WecomEngineAdapter(
        connection,
        identityMapper,
        new WecomOpenApiHttpClient(
            connection, credential, restTemplate, tokenCache, overallTimeoutMillis),
        credential);
  }

  private WecomCredential credential(ResolvedEnterpriseConnection connection) {
    if (connection == null || connection.credential() == null) {
      return null;
    }
    if (connection.credential() instanceof WecomCredential value) {
      return value;
    }
    throw new BusinessException(
        "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
            + "] 企业微信连接凭据类型不匹配: " + connection.connectionId());
  }
}
