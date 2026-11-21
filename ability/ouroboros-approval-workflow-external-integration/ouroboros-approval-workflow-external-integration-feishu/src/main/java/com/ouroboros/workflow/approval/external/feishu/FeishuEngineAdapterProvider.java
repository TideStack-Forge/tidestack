package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuTokenCache;

import com.ouroboros.enterprise.feishu.FeishuCredential;

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

/** Provider for Feishu native approval as an external workflow authority. */
public class FeishuEngineAdapterProvider implements ApprovalWorkflowEngineAdapterProvider {

  public static final String ADAPTER_ID = "feishu-approval-native";

  private final ExternalUserIdentityMapper identityMapper;
  private final org.springframework.web.client.RestTemplate restTemplate;
  private final FeishuTokenCache tokenCache;
  private final long overallTimeoutMillis;

  public FeishuEngineAdapterProvider() {
    this(null, spiRestTemplate(), new FeishuTokenCache(),
        FeishuOpenApiHttpClient.DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  FeishuEngineAdapterProvider(
      ExternalUserIdentityMapper identityMapper,
      org.springframework.web.client.RestTemplate restTemplate,
      FeishuTokenCache tokenCache,
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
    return "feishu";
  }





  @Override
  public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
    return new ApprovalWorkflowEngineAdapterDescriptor(
        ADAPTER_ID,
        "飞书原生审批",
        "1.0",
        Set.of(),
        Set.of(Capability.EXTERNAL_FINAL_RESULT),
        Set.of());
  }

  @Override
  public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
    FeishuCredential credential = credential(connection);
    return new FeishuEngineAdapter(
        connection,
        identityMapper,
        new FeishuOpenApiHttpClient(
            connection, credential, restTemplate, tokenCache, overallTimeoutMillis),
        credential);
  }

  private FeishuCredential credential(ResolvedEnterpriseConnection connection) {
    if (connection == null || connection.credential() == null) {
      return null;
    }
    if (connection.credential() instanceof FeishuCredential value) {
      return value;
    }
    throw new BusinessException(
        "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
            + "] 飞书连接凭据类型不匹配: " + connection.connectionId());
  }
}
