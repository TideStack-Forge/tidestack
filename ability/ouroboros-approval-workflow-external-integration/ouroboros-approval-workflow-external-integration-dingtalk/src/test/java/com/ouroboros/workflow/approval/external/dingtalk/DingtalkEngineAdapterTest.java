package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.CcTarget;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;

/**
 * M17 钉钉适配器单元测试：用户翻译契约、发起组装、复合表单拒绝、健康检查与能力收敛。
 */
class DingtalkEngineAdapterTest {

  private static final DingtalkCredential TEST_CREDENTIAL =
      DingtalkCredential.internalApp("app-key", "app-secret", "callback-token", "encoding-aes-key");

  private DingtalkEngineAdapter adapter(FakeDingtalkOpenApiClient client) {
    return new DingtalkEngineAdapter(
        new ResolvedEnterpriseConnection(
            "conn-1", Map.of("corpId", "corp-123"), TEST_CREDENTIAL),
        new TestDingtalkUserIdentityMapper(), client, TEST_CREDENTIAL);
  }

  private ExternalStartCommand startCommand() {
    return new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("corp-a", "PROC_EXPENSE", null, "hash"),
        new ExternalStartCommand.ExternalBusinessRecord("expense", "e-1"),
        Map.of("amount", 100), null, "ik-1", null, null, null);
  }

  @Test
  void startTranslatesUserAndAssemblesFormValues() {
    FakeDingtalkOpenApiClient client = new FakeDingtalkOpenApiClient();
    DingtalkEngineAdapter adapter = adapter(client);

    ExternalStartResult result = adapter.start(startCommand()).get();

    assertThat(result.externalInstanceId()).isEqualTo("inst-1");
    assertThat(client.startCalls).hasSize(1);
    FakeDingtalkOpenApiClient.StartCall call = client.startCalls.get(0);
    assertThat(call.originatorUserId()).isEqualTo("ding-1");
    assertThat(call.processCode()).isEqualTo("PROC_EXPENSE");
    assertThat(call.formComponentValues()).containsExactly(Map.of("name", "amount", "value", "100"));
  }

  @Test
  void startWithoutUserMappingFailsWithMappingMissing() {
    DingtalkEngineAdapter adapter = new DingtalkEngineAdapter(
        new ResolvedEnterpriseConnection("conn-1", Map.of(), TEST_CREDENTIAL),
        new ExternalUserIdentityMapperNoop(), new FakeDingtalkOpenApiClient(), TEST_CREDENTIAL);

    var result = adapter.start(startCommand());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING);
  }

  @Test
  void startWithCompositeFormValueFailsAsUnsupported() {
    FakeDingtalkOpenApiClient client = new FakeDingtalkOpenApiClient();
    DingtalkEngineAdapter adapter = adapter(client);
    ExternalStartCommand command = new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("corp-a", "PROC_EXPENSE", null, "hash"),
        null, Map.of("attachment", Map.of("fileId", "1")), null, "ik-2", null, null, null);

    var result = adapter.start(command);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    assertThat(client.startCalls).isEmpty();
  }

  @Test
  void startWithApproverOverrideFailsBeforeRemoteCall() {
    FakeDingtalkOpenApiClient client = new FakeDingtalkOpenApiClient();
    DingtalkEngineAdapter adapter = adapter(client);
    ExternalStartCommand command = new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("corp-a", "PROC_EXPENSE", null, "hash"),
        null, Map.of("amount", 100), null, "ik-approver", null,
        Map.of("node-1", List.of("user-2")), null);

    var result = adapter.start(command);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    assertThat(client.startCalls).isEmpty();
  }

  @Test
  void startWithCcFailsBeforeRemoteCall() {
    FakeDingtalkOpenApiClient client = new FakeDingtalkOpenApiClient();
    DingtalkEngineAdapter adapter = adapter(client);
    ExternalStartCommand command = new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("corp-a", "PROC_EXPENSE", null, "hash"),
        null, Map.of("amount", 100), null, "ik-cc",
        List.of(new CcTarget("user", "user-2", CcTarget.POSITION_START)), null, null);

    var result = adapter.start(command);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    assertThat(client.startCalls).isEmpty();
  }

  @Test
  void descriptorIsResultOnlyAndDoesNotExposeTaskActionCapabilities() {
    DingtalkEngineAdapterProvider provider = new DingtalkEngineAdapterProvider(
        new TestDingtalkUserIdentityMapper(), new org.springframework.web.client.RestTemplate()) {
      @Override
      public com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter create(
          ResolvedEnterpriseConnection connection) {
        return new DingtalkEngineAdapter(connection, new TestDingtalkUserIdentityMapper(),
            new FakeDingtalkOpenApiClient(), TEST_CREDENTIAL);
      }
    };
    WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));

    registry.initialize(List.of(provider));

    assertThat(registry.integrationModeOf(provider.descriptor().adapterId()))
        .isEqualTo(WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY);
    assertThat(provider.descriptor().canonicalActions()).isEmpty();
    assertThat(registry.capabilitiesOf(DingtalkEngineAdapterProvider.ADAPTER_ID))
        .doesNotContain(Capability.EXTERNAL_TASK_QUERY, Capability.EXTERNAL_TASK_ACTION)
        .contains(Capability.EXTERNAL_START, Capability.EXTERNAL_INSTANCE_QUERY, Capability.EXTERNAL_FINAL_RESULT,
            Capability.CALLBACK);
    assertThat(registry.capabilitiesOf(DingtalkEngineAdapterProvider.ADAPTER_ID))
        .doesNotContain(Capability.POLLING, Capability.DEFINITION_DISCOVERY,
            Capability.EXTERNAL_START_RECOVERY);
  }

  @Test
  void healthReflectsTokenFailure() {
    FakeDingtalkOpenApiClient client = new FakeDingtalkOpenApiClient();
    client.failToken = true;

    ExternalHealth health = adapter(client).health().get();

    assertThat(health.healthy()).isFalse();
  }

  @Test
  void startAppLinkUsesCorpIdFromConnection() {
    Map<String, Object> config = new java.util.LinkedHashMap<>();
    config.put("corpId", "corp-123");
    DingtalkEngineAdapter adapter = new DingtalkEngineAdapter(
        new ResolvedEnterpriseConnection("conn-1", config, TEST_CREDENTIAL),
        new TestDingtalkUserIdentityMapper(), new FakeDingtalkOpenApiClient(),
        TEST_CREDENTIAL);

    ExternalStartResult result = adapter.start(startCommand()).get();

    assertThat(result.externalUrl()).contains("corpId=corp-123");
    assertThat(result.externalUrl()).contains("from=app-key");
    assertThat(result.externalUrl()).doesNotContain("__CORP_ID__");
  }

  @Test
  void startFailsBeforeRemoteCallWithoutCorpId() {
    FakeDingtalkOpenApiClient client = new FakeDingtalkOpenApiClient();
    DingtalkEngineAdapter adapter = new DingtalkEngineAdapter(
        new ResolvedEnterpriseConnection("conn-1", Map.of(), TEST_CREDENTIAL),
        new TestDingtalkUserIdentityMapper(), client, TEST_CREDENTIAL);

    var result = adapter.start(startCommand());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("corpId");
    assertThat(client.startCalls).isEmpty();
  }

  /** 永不命中的映射器（映射缺失场景）。 */
  static final class ExternalUserIdentityMapperNoop
      implements com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper {
    @Override
    public java.util.Optional<String> toExternalUser(String platformUserId, String adapterId, String connectionId) {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> toPlatformUser(String externalUserId, String adapterId, String connectionId) {
      return java.util.Optional.empty();
    }
  }
}
