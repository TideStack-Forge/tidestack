package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCrypto;

import com.ouroboros.enterprise.dingtalk.DingtalkConnectionCredentialMapper;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.externalauth.secret.FileSecretResolver;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestService;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import io.vavr.control.Try;

/**
 * DingTalk RESULT_ONLY 发布门禁契约测试。
 *
 * <p>本测试不依赖真实钉钉环境；它用正式 connection provider、credentialRef resolver、
 * DingTalk adapter、HTTP client、callback codec、receipt service 和 inbox ingress 边界跑一遍
 * RESULT_ONLY 合同，并用 mock HTTP 断言请求与钉钉官方接口文档一致。真实联调保留为人工/实验证据，
 * 但不作为本模块上线前的自动门禁。
 */
class DingtalkResultOnlyReleaseContractTest {

  private static final String CONNECTION_ID = "conn-release";
  private static final String CORP_ID = "ding-corp";
  private static final String PROCESS_CODE = "PROC_EXPENSE";
  private static final String PLATFORM_USER_ID = "platform-user-1";
  private static final String DINGTALK_USER_ID = "ding-user-1";
  private static final String TOKEN = "callback-token";
  private static final String ENCODING_AES_KEY =
      java.util.Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
          .substring(0, 43);

  @TempDir
  private Path tempDir;

  @Test
  void resultOnlyReleaseContractAlignsOfficialStartAndCallbackWithoutLiveDingtalk() throws Exception {
    EnterpriseConnectionCatalog connectionProvider = connectionProvider(writeCredentialFile());
    ResolvedEnterpriseConnection connection = connectionProvider
        .resolve(CONNECTION_ID, "dingtalk")
        .orElseThrow();

    assertThat(connection.credential()).isInstanceOf(DingtalkCredential.class);
    assertThat(connection.config().get("corpId")).isEqualTo(CORP_ID);
    assertThat(connection.config()).doesNotContainKey("callbackUrl");

    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/oauth2/accessToken"),
        argThat(arg -> arg instanceof Map<?, ?> body
            && "app-key".equals(body.get("appKey"))
            && "app-secret".equals(body.get("appSecret"))),
        eq(String.class)))
        .thenReturn("{\"accessToken\":\"access-token-1\",\"expireIn\":7200}");
    when(restTemplate.postForObject(
        eq("https://api.dingtalk.com/v1.0/workflow/processInstances"),
        argThat(arg -> arg instanceof HttpEntity<?> entity
            && "access-token-1".equals(entity.getHeaders().getFirst("x-acs-dingtalk-access-token"))
            && entity.getBody() instanceof Map<?, ?> body
            && DINGTALK_USER_ID.equals(body.get("originatorUserId"))
            && PROCESS_CODE.equals(body.get("processCode"))
            && Integer.valueOf(-1).equals(body.get("deptId"))
            && body.containsKey("formComponentValues")
            && !body.containsKey("approvers")
            && !body.containsKey("ccList")),
        eq(String.class)))
        .thenReturn("{\"instanceId\":\"ding-instance-1\"}");

    ConnectionAwareAdapterResolver adapterResolver = adapterResolver(connectionProvider, restTemplate);
    ExternalWorkflowStarter starter = adapterResolver.resolvePort(
        DingtalkEngineAdapterProvider.ADAPTER_ID, CONNECTION_ID, ExternalWorkflowStarter.class);

    Try<ExternalStartResult> started = starter.start(new ExternalStartCommand(
        PLATFORM_USER_ID,
        new ExternalProcessDefinitionRef(CORP_ID, PROCESS_CODE, null, null),
        null,
        Map.of("amount", 100),
        null,
        "result-only-release-contract",
        null,
        null,
        null));

    assertThat(started.isSuccess()).isTrue();
    assertThat(started.get().externalInstanceId()).isEqualTo("ding-instance-1");
    assertThat(started.get().externalUrl())
        .contains("corpId=" + CORP_ID)
        .contains("instanceId=ding-instance-1")
        .contains("from=app-key")
        .doesNotContain("__CORP_ID__");

    ExternalEventIngestService ingestService = mock(ExternalEventIngestService.class);
    when(ingestService.ingest(any())).thenReturn(Try.success(null));
    var receipts = mock(com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService.class);
    when(receipts.receive(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
      java.util.List<ExternalCallbackEnvelope> events = invocation.getArgument(4);
      for (var envelope : events) { ingestService.ingest(envelope).get(); }
      return Try.success(null);
    });
    var controller = new com.ouroboros.enterprise.runtime.callback.EnterpriseCallbackDispatcher(connectionProvider,
        java.util.List.of(new com.ouroboros.enterprise.dingtalk.DingtalkCallbackProtocol()),
        java.util.List.of(new DingtalkApprovalCallbackSubscriber(receipts)));
    String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
    String encrypted = DingtalkCryptoTestSupport.encrypt(ENCODING_AES_KEY, """
        {"EventType":"bpms_instance_change","processInstanceId":"ding-instance-1",
         "type":"finish","result":"agree","createTime":"2026-08-27 17:00:00","corpId":"ding-corp"}
        """, CORP_ID);
    String signature = DingtalkCrypto.sha1Signature(TOKEN, timestamp, "release-nonce", encrypted);

    var response = controller.receive(
        CONNECTION_ID, new com.ouroboros.enterprise.callback.EnterpriseCallbackRequest("POST", Map.of(),
            Map.of("signature", signature, "timestamp", timestamp, "nonce", "release-nonce"),
            "{\"encrypt\":\"" + encrypted + "\"}"));

    assertThat(response.contentType()).isEqualTo("application/json");
    var acknowledgement = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
    assertThat(DingtalkCrypto.decrypt(ENCODING_AES_KEY, acknowledgement.path("encrypt").asText(), CORP_ID))
        .isEqualTo("success");
    ArgumentCaptor<ExternalCallbackEnvelope> event =
        ArgumentCaptor.forClass(ExternalCallbackEnvelope.class);
    verify(ingestService).ingest(event.capture());
    assertThat(event.getValue().externalInstanceId()).isEqualTo("ding-instance-1");
    assertThat(event.getValue().resultCode()).isEqualTo("APPROVED");
  }

  private String writeCredentialFile() throws Exception {
    Path credentialFile = tempDir.resolve("dingtalk-credential.json");
    Files.writeString(credentialFile, """
        {
          "appKey": "app-key",
          "appSecret": "app-secret",
          "callbackToken": "callback-token",
          "encodingAesKey": "%s"
        }
        """.formatted(ENCODING_AES_KEY), StandardCharsets.UTF_8);
    return "file:" + credentialFile;
  }

  private static EnterpriseConnectionCatalog connectionProvider(String credentialRef) {
    ConfigurationService configurationService = connectionConfiguration(credentialRef);
    return new EnterpriseConnectionCatalog(
        configurationService,
        Optional.of(new CompositeSecretResolver(List.of(new FileSecretResolver()))),
        List.of(new DingtalkConnectionCredentialMapper()),
        List.of(new com.ouroboros.enterprise.dingtalk.DingtalkConnectionProvider()));
  }

  private static ConfigurationService connectionConfiguration(String credentialRef) {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY))
        .thenReturn(Optional.of(new com.ouroboros.configuration.ConfigurationSnapshot(
            EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, List.of(Map.of(
            "provider", "dingtalk",
            "connectionId", CONNECTION_ID,
            "credentialRef", credentialRef,
            "corpId", CORP_ID)), null, "revision-1", null)));
    return configurationService;
  }

  private static ConnectionAwareAdapterResolver adapterResolver(
      EnterpriseConnectionCatalog connectionProvider,
      RestTemplate restTemplate) {
    WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(new DingtalkEngineAdapterProvider(
        new ContractIdentityMapper(), restTemplate)));
    return new ConnectionAwareAdapterResolver(registry, connectionProvider);
  }

  private static final class ContractIdentityMapper implements ExternalUserIdentityMapper {
    @Override
    public Optional<String> toExternalUser(String platformUserId, String adapterId, String connectionId) {
      if (DingtalkEngineAdapterProvider.ADAPTER_ID.equals(adapterId)
          && CONNECTION_ID.equals(connectionId)
          && PLATFORM_USER_ID.equals(platformUserId)) {
        return Optional.of(DINGTALK_USER_ID);
      }
      return Optional.empty();
    }

    @Override
    public Optional<String> toPlatformUser(String externalUserId, String adapterId, String connectionId) {
      if (DingtalkEngineAdapterProvider.ADAPTER_ID.equals(adapterId)
          && CONNECTION_ID.equals(connectionId)
          && DINGTALK_USER_ID.equals(externalUserId)) {
        return Optional.of(PLATFORM_USER_ID);
      }
      return Optional.empty();
    }
  }
}
