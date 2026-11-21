package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuTokenCache;

import com.ouroboros.enterprise.feishu.FeishuCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryQuery;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryResult;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;

import io.vavr.control.Try;

class FeishuEngineAdapterContractTest {

  private static final FeishuCredential CREDENTIAL = new FeishuCredential(
      "app-id", "app-secret", "verification-token", null);

  @Test
  void providerExposesOnlyImplementedResultOnlyCapabilities() {
    FeishuEngineAdapterProvider provider = new FeishuEngineAdapterProvider(
        mapping("platform-user", "ou_external"), mock(RestTemplate.class),
        new FeishuTokenCache(), 1_000L);
    WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));

    registry.initialize(List.of(provider));

    assertThat(registry.integrationModeOf(provider.descriptor().adapterId()))
        .isEqualTo(WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY);
    assertThat(provider.descriptor().canonicalActions()).isEmpty();
    assertThat(registry.capabilitiesOf(FeishuEngineAdapterProvider.ADAPTER_ID))
        .contains(Capability.DEFINITION_VALIDATION, Capability.DEFINITION_FORM,
            Capability.EXTERNAL_START, Capability.EXTERNAL_START_RECOVERY,
            Capability.EXTERNAL_INSTANCE_QUERY, Capability.EXTERNAL_FINAL_RESULT,
            Capability.CALLBACK)
        .doesNotContain(Capability.POLLING, Capability.EXTERNAL_TASK_ACTION);
    var adapter = provider.create(connection(CREDENTIAL));
    assertThat(adapter).isInstanceOf(ExternalInstanceDetailReader.class)
        .isInstanceOf(ExternalCallbackCodec.class)
        .isNotInstanceOf(ExternalInstanceReader.class)
        .isNotInstanceOf(ExternalWorkflowActionExecutor.class);
  }

  @Test
  void startMapsIdentityAndUsesStableUuid() {
    RecordingClient client = new RecordingClient();
    FeishuEngineAdapter adapter = new FeishuEngineAdapter(
        connection(CREDENTIAL), mapping("platform-user", "ou_external"), client, CREDENTIAL);

    Try<ExternalStartResult> first = adapter.start(command("stable-fingerprint"));
    String firstUuid = client.uuid;
    Try<ExternalStartResult> second = adapter.start(command("stable-fingerprint"));

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(client.externalUserId).isEqualTo("ou_external");
    assertThat(client.externalDepartmentId).isEqualTo("od_department");
    assertThat(client.formJson).contains("\"id\":\"reason\"")
        .contains("\"type\":\"input\"")
        .contains("\"value\":\"travel\"");
    assertThat(client.uuid).isEqualTo(firstUuid)
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void recoveryUsesTheSameStableRequestIdentity() {
    RecordingClient client = new RecordingClient();
    FeishuEngineAdapter adapter = new FeishuEngineAdapter(
        connection(CREDENTIAL), mapping("platform-user", "ou_external"), client, CREDENTIAL);
    adapter.start(command("stable-fingerprint"));
    String startUuid = client.uuid;

    Try<ExternalStartRecoveryResult> recovered = adapter.recover(
        new ExternalStartRecoveryQuery(
            "business-idempotency", "stable-fingerprint", "scope-1", "flow-1",
            "record-type", "record-1", "mapping-1", 1));

    assertThat(recovered.isSuccess()).isTrue();
    assertThat(client.recoveryUuid).isEqualTo(startUuid);
  }

  @Test
  void missingIdentityMappingStopsBeforeRemoteCall() {
    FeishuOpenApiClient client = mock(FeishuOpenApiClient.class);
    FeishuEngineAdapter adapter = new FeishuEngineAdapter(
        connection(CREDENTIAL), mapping("another-user", "ou_external"), client, CREDENTIAL);

    Try<ExternalStartResult> result = adapter.start(command("stable-fingerprint"));

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("EXTERNAL_USER_MAPPING_MISSING");
    verifyNoInteractions(client);
  }

  @Test
  void missingStableIdentityAndUnknownFormFieldsFailClosed() {
    RecordingClient client = new RecordingClient();
    FeishuEngineAdapter adapter = new FeishuEngineAdapter(
        connection(CREDENTIAL), mapping("platform-user", "ou_external"), client, CREDENTIAL);
    ExternalStartCommand noIdentity = new ExternalStartCommand(
        "platform-user", definition(), null, Map.of("reason", "travel"), null,
        null, null, null, null);
    ExternalStartCommand unknownField = new ExternalStartCommand(
        "platform-user", definition(), null, Map.of("unknown", "travel"), null,
        "stable-fingerprint", null, null, null);

    Try<ExternalStartResult> missing = adapter.start(noIdentity);
    Try<ExternalStartResult> unknown = adapter.start(unknownField);

    assertThat(missing.isFailure()).isTrue();
    assertThat(missing.getCause().getMessage()).contains("稳定请求身份");
    assertThat(unknown.isFailure()).isTrue();
    assertThat(unknown.getCause().getMessage()).contains("EXTERNAL_MAPPING_INVALID");
    assertThat(client.createCalls).isZero();
  }

  private static ResolvedEnterpriseConnection connection(FeishuCredential credential) {
    return new ResolvedEnterpriseConnection(
        "conn-1", Map.of("departmentId", "od_department"), credential);
  }

  private static ExternalStartCommand command(String requestIdentity) {
    return new ExternalStartCommand(
        "platform-user", definition(), null, Map.of("reason", "travel"), null,
        requestIdentity, null, null, null);
  }

  private static ExternalProcessDefinitionRef definition() {
    return new ExternalProcessDefinitionRef("tenant-1", "approval-code", null, null);
  }

  private static ExternalUserIdentityMapper mapping(String platformUser, String externalUser) {
    return new ExternalUserIdentityMapper() {
      @Override
      public Optional<String> toExternalUser(
          String platformUserId, String adapterId, String connectionId) {
        return platformUser.equals(platformUserId)
            && FeishuEngineAdapterProvider.ADAPTER_ID.equals(adapterId)
            && "conn-1".equals(connectionId) ? Optional.of(externalUser) : Optional.empty();
      }

      @Override
      public Optional<String> toPlatformUser(
          String externalUserId, String adapterId, String connectionId) {
        return externalUser.equals(externalUserId) ? Optional.of(platformUser) : Optional.empty();
      }
    };
  }

  private static final class RecordingClient implements FeishuOpenApiClient {
    private String externalUserId;
    private String externalDepartmentId;
    private String formJson;
    private String uuid;
    private String recoveryUuid;
    private int createCalls;

    @Override
    public Try<String> accessToken() {
      return Try.success("token");
    }

    @Override
    public Try<ExternalProcessDefinition> validateApproval(String ignored) {
      return Try.success(new ExternalProcessDefinition(
          "approval-code", "version-1", "Travel", "hash"));
    }

    @Override
    public Try<ExternalFormSchema> getApprovalForm(String ignored) {
      return Try.success(new ExternalFormSchema(
          List.of(Map.of("id", "reason", "type", "input")),
          Map.of("approvalName", "Travel", "schema", "[]")));
    }

    @Override
    public Try<String> createInstance(
        String ignored, String userId, String departmentId, String form, String requestUuid) {
      externalUserId = userId;
      externalDepartmentId = departmentId;
      formJson = form;
      uuid = requestUuid;
      createCalls++;
      return Try.success("FS-20260905-1");
    }

    @Override
    public Try<ExternalInstanceSnapshot> getInstance(String externalInstanceId) {
      return Try.success(new ExternalInstanceSnapshot(
          externalInstanceId, "RUNNING", null, null, null, Map.of()));
    }

    @Override
    public Try<ExternalStartRecoveryResult> recoverStart(String requestUuid) {
      recoveryUuid = requestUuid;
      return Try.success(ExternalStartRecoveryResult.found(new ExternalStartResult(
          "FS-20260905-1", null, null)));
    }
  }
}
