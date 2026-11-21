package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkTokenCache;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import java.util.Map;
import java.util.List;
import java.util.Set;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;

/**
 * 钉钉预置适配器 Provider（设计文档 §钉钉预置适配器）。
 *
 * <p>默认稳定契约只开放 RESULT_ONLY + LINK（流程中心 mirror 已随 MIRROR_* 删除）。
 * FULL_PROCESS 仍保留为研究/contract profile 议题，不作为默认 provider 能力声明。
 */
public class DingtalkEngineAdapterProvider implements ApprovalWorkflowEngineAdapterProvider {

  public static final String ADAPTER_ID = "dingtalk-oa";

  private final ExternalUserIdentityMapper identityMapper;
  private final org.springframework.web.client.RestTemplate restTemplate;
  private final DingtalkTokenCache tokenCache;
  private final long overallTimeoutMillis;

  /** 无依赖构造（SPI 场景/测试）：身份映射缺失时 start/execute 返回 EXTERNAL_USER_MAPPING_MISSING。 */
  public DingtalkEngineAdapterProvider() {
    this(null, spiRestTemplate(), new DingtalkTokenCache(),
        DingtalkOpenApiHttpClient.DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  /** Bean 注册构造（AutoConfiguration 注入身份映射器与 HTTP 客户端）。 */
  public DingtalkEngineAdapterProvider(
      ExternalUserIdentityMapper identityMapper,
      org.springframework.web.client.RestTemplate restTemplate) {
    this(identityMapper, restTemplate, new DingtalkTokenCache(),
        DingtalkOpenApiHttpClient.DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  DingtalkEngineAdapterProvider(
      ExternalUserIdentityMapper identityMapper,
      org.springframework.web.client.RestTemplate restTemplate,
      DingtalkTokenCache tokenCache) {
    this(identityMapper, restTemplate, tokenCache,
        DingtalkOpenApiHttpClient.DEFAULT_OVERALL_TIMEOUT_MILLIS);
  }

  DingtalkEngineAdapterProvider(
      ExternalUserIdentityMapper identityMapper,
      org.springframework.web.client.RestTemplate restTemplate,
      DingtalkTokenCache tokenCache,
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
    return "dingtalk";
  }







  @Override
  public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
    return new ApprovalWorkflowEngineAdapterDescriptor(
        ADAPTER_ID, "钉钉 OA 审批", "1.0",
        Set.of(),
        Set.of(Capability.EXTERNAL_FINAL_RESULT),
        Set.of());
  }

  @Override
  public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
    DingtalkCredential credential = credential(connection);
    return new DingtalkEngineAdapter(
        connection,
        identityMapper,
        new DingtalkOpenApiHttpClient(
            connection, credential, restTemplate, tokenCache, overallTimeoutMillis),
        credential);
  }

  private DingtalkCredential credential(ResolvedEnterpriseConnection connection) {
    if (connection == null || connection.credential() == null) {
      return null;
    }
    if (connection.credential() instanceof DingtalkCredential credential) {
      return credential;
    }
    throw new BusinessException(
        "[" + com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
            + "] 钉钉连接凭据类型不匹配: " + connection.connectionId());
  }
}
