package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomTokenCache;

import com.ouroboros.enterprise.wecom.WecomCredential;

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
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;

import io.vavr.control.Try;

class WecomEngineAdapterContractTest {

  private static final WecomCredential CREDENTIAL = new WecomCredential(
      "suite-1", "suite-secret", "suite-ticket", "permanent-code",
      "callback-token", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");

  @Test
  void providerExposesOnlyImplementedResultOnlyCapabilities() {
    WecomEngineAdapterProvider provider = new WecomEngineAdapterProvider(
        mapping("platform-user", "wecom-user"), mock(RestTemplate.class),
        new WecomTokenCache(), 1_000L);
    WorkflowEngineAdapterRegistry registry = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));

    registry.initialize(List.of(provider));

    assertThat(registry.integrationModeOf(provider.descriptor().adapterId()))
        .isEqualTo(WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY);
    assertThat(provider.descriptor().canonicalActions()).isEmpty();
    assertThat(registry.capabilitiesOf(WecomEngineAdapterProvider.ADAPTER_ID))
        .contains(Capability.DEFINITION_VALIDATION, Capability.DEFINITION_FORM,
            Capability.EXTERNAL_START, Capability.EXTERNAL_INSTANCE_QUERY,
            Capability.EXTERNAL_FINAL_RESULT, Capability.CALLBACK)
        .doesNotContain(Capability.POLLING, Capability.EXTERNAL_TASK_ACTION);
    var adapter = provider.create(connection(CREDENTIAL));
    assertThat(adapter).isInstanceOf(ExternalInstanceDetailReader.class)
        .isInstanceOf(ExternalCallbackCodec.class)
        .isNotInstanceOf(ExternalInstanceReader.class)
        .isNotInstanceOf(ExternalWorkflowActionExecutor.class);
  }

  @Test
  void startMapsIdentityAndValidatedTextFields() {
    RecordingClient client = new RecordingClient();
    WecomEngineAdapter adapter = new WecomEngineAdapter(
        connection(CREDENTIAL), mapping("platform-user", "wecom-user"), client, CREDENTIAL);

    Try<ExternalStartResult> result = adapter.start(command(Map.of("reason", "travel")));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().externalInstanceId()).isEqualTo("WE-20260905-1");
    assertThat(client.creatorUserId).isEqualTo("wecom-user");
    assertThat(client.templateId).isEqualTo("template-1");
    assertThat(client.contents).containsExactly(Map.of(
        "control", "Text", "id", "reason", "value", Map.of("text", "travel")));
  }

  @Test
  void missingIdentityMappingStopsBeforeRemoteCall() {
    WecomOpenApiClient client = mock(WecomOpenApiClient.class);
    WecomEngineAdapter adapter = new WecomEngineAdapter(
        connection(CREDENTIAL), mapping("another-user", "wecom-user"), client, CREDENTIAL);

    Try<ExternalStartResult> result = adapter.start(command(Map.of("reason", "travel")));

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("EXTERNAL_USER_MAPPING_MISSING");
    verifyNoInteractions(client);
  }

  @Test
  void unsupportedStartOverridesAndUnknownFieldsFailClosed() {
    RecordingClient client = new RecordingClient();
    WecomEngineAdapter adapter = new WecomEngineAdapter(
        connection(CREDENTIAL), mapping("platform-user", "wecom-user"), client, CREDENTIAL);
    ExternalStartCommand override = new ExternalStartCommand(
        "platform-user", definition(), null, Map.of("reason", "travel"), null,
        "fact-1", null, Map.of("node-1", List.of("platform-user")), null);

    Try<ExternalStartResult> unsupported = adapter.start(override);
    Try<ExternalStartResult> unknownField = adapter.start(command(Map.of("unknown", "value")));

    assertThat(unsupported.isFailure()).isTrue();
    assertThat(unsupported.getCause().getMessage()).contains("EXTERNAL_CAPABILITY_UNSUPPORTED");
    assertThat(unknownField.isFailure()).isTrue();
    assertThat(unknownField.getCause().getMessage()).contains("EXTERNAL_MAPPING_INVALID");
    assertThat(client.startCalls).isZero();
  }

  private static ResolvedEnterpriseConnection connection(WecomCredential credential) {
    return new ResolvedEnterpriseConnection(
        "conn-1", Map.of("corpId", "corp-1"), credential);
  }

  private static ExternalStartCommand command(Map<String, Object> formData) {
    return new ExternalStartCommand(
        "platform-user", definition(), null, formData, null, "fact-1",
        null, null, null);
  }

  private static ExternalProcessDefinitionRef definition() {
    return new ExternalProcessDefinitionRef("corp-1", "template-1", null, null);
  }

  private static ExternalUserIdentityMapper mapping(String platformUser, String externalUser) {
    return new ExternalUserIdentityMapper() {
      @Override
      public Optional<String> toExternalUser(
          String platformUserId, String adapterId, String connectionId) {
        return platformUser.equals(platformUserId)
            && WecomEngineAdapterProvider.ADAPTER_ID.equals(adapterId)
            && "conn-1".equals(connectionId) ? Optional.of(externalUser) : Optional.empty();
      }

      @Override
      public Optional<String> toPlatformUser(
          String externalUserId, String adapterId, String connectionId) {
        return externalUser.equals(externalUserId) ? Optional.of(platformUser) : Optional.empty();
      }
    };
  }

  private static final class RecordingClient implements WecomOpenApiClient {
    private String creatorUserId;
    private String templateId;
    private List<Map<String, Object>> contents;
    private int startCalls;

    @Override
    public Try<String> accessToken() {
      return Try.success("token");
    }

    @Override
    public Try<ExternalProcessDefinition> validateTemplate(String ignored) {
      return Try.success(new ExternalProcessDefinition("template-1", null, "Travel", "hash"));
    }

    @Override
    public Try<ExternalFormSchema> getTemplateSchema(String ignored) {
      return Try.success(new ExternalFormSchema(List.of(Map.of(
          "property", Map.of("id", "reason", "control", "Text"))),
          Map.of("templateName", "Travel", "schema", "{}")));
    }

    @Override
    public Try<String> startApproval(
        String creator, String template, List<Map<String, Object>> applyContents,
        String ignored) {
      creatorUserId = creator;
      templateId = template;
      contents = applyContents;
      startCalls++;
      return Try.success("WE-20260905-1");
    }

    @Override
    public Try<ExternalInstanceSnapshot> getApprovalDetail(String externalInstanceId) {
      return Try.success(new ExternalInstanceSnapshot(
          externalInstanceId, "RUNNING", null, null, null, Map.of()));
    }
  }
}
