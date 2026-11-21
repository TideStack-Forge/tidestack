package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalActionCommand;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;

import io.vavr.control.Try;

/**
 * 外部引擎适配器契约测试基座（M12-T2）。
 *
 * <p>面向未来所有真实适配器：子类只需提供 {@link ApprovalWorkflowEngineAdapterProvider}。
 * 运行时层规则（inbox 去重、映射校验、动作映射）由各服务测试覆盖，本基座只断言适配器自身契约；
 * 与运行时联动的断言（如"未知结果码不自动结束"）以适配器侧状态机行为表达。
 */
public abstract class AbstractExternalEngineAdapterContractTest {

  private WorkflowEngineAdapterRegistry registry;
  private RegisteredAdapter registered;
  private ConnectionAwareAdapterResolver adapterResolver;
  private ApprovalWorkflowEngineAdapter adapter;

  protected abstract ApprovalWorkflowEngineAdapterProvider provider();

  /** 适配器是否实现定义目录端口（FULL_PROCESS fake 开启）。 */
  protected boolean supportsDefinitionCatalog() {
    return false;
  }

  /** 适配器是否实现动作执行端口（FULL_PROCESS fake 开启）。 */
  protected boolean supportsActionExecution() {
    return false;
  }

  @BeforeEach
  void setUp() {
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(provider()));
    registered = registry.getAdapter(provider().descriptor().adapterId()).orElseThrow();
    adapterResolver = ConnectionAwareAdapterResolverTestSupport.resolver(registry);
    adapter = adapterResolver.resolve(provider().descriptor().adapterId(), "conn-1");
  }

  @Test
  void descriptorCapabilitiesConsistentWithPorts() {
    assertThat(registered.capabilities()).noneMatch(cap -> cap.name().startsWith("LINK_"));
    // M13：描述符模式字段一致性（与 WorkflowEngineMappingValidator.CANONICAL_ACTIONS 同步维护）
    assertThat(provider().descriptor().disabledActions())
        .allMatch(action -> WorkflowEngineMappingValidator.CANONICAL_ACTIONS.contains(action));
    // 与 Registry 启动期校验共用同一张 能力→端口 表（EngineAdapterCapabilities.PORT_BY_CAPABILITY）
    for (Capability extra : provider().descriptor().extraCapabilities()) {
      Class<?> port = EngineAdapterCapabilities.PORT_BY_CAPABILITY.get(extra);
      if (port != null) {
        assertThat(port.isInstance(adapter))
            .as("extraCapabilities %s 需实现端口 %s", extra, port.getSimpleName())
            .isTrue();
      }
    }
  }

  @Test
  void startIsIdempotentByKey() {
    assumeTrue(registered.hasCapability(Capability.EXTERNAL_START));
    ExternalWorkflowStarter starter = adapterResolver.resolvePort(
        provider().descriptor().adapterId(), "conn-1", ExternalWorkflowStarter.class);
    ExternalStartCommand command = new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("ns-1", "def-1", "v3", "hash-3"),
        null, null, null, "ik-1", null, null, null);

    Try<ExternalStartResult> first = starter.start(command);
    Try<ExternalStartResult> second = starter.start(command);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(second.get().externalInstanceId()).isEqualTo(first.get().externalInstanceId());
  }

  @Test
  void validateDefinitionDoesNotSilentlyBindLatest() {
    assumeTrue(supportsDefinitionCatalog());
    ExternalProcessDefinitionValidator catalog =
        adapterResolver.resolvePort(
            provider().descriptor().adapterId(), "conn-1", ExternalProcessDefinitionValidator.class);

    Try<com.ouroboros.workflow.approval.external.ExternalProcessDefinition> result =
        catalog.validateDefinition(new ExternalProcessDefinitionRef("ns-1", "def-1", "v3", "hash-3"));

    assertThat(result.isSuccess()).isTrue();
    // 版本允许为 null（钉钉等无统一模板版本）：返回 null 时不得隐式补"最新"，非 null 必须原样返回
    if (result.get().definitionVersion() != null) {
      assertThat(result.get().definitionVersion()).isEqualTo("v3");
    }
    if (adapter instanceof FakeFullProcessEngineAdapter fake) {
      assertThat(fake.validatedDefinitions()).hasSize(1);
      assertThat(fake.validatedDefinitions().get(0).definitionVersion()).isEqualTo("v3");
    }
  }

  @Test
  protected void callbackDuplicateEventIsIdempotent() {
    assumeTrue(registered.hasCapability(Capability.CALLBACK));
    ExternalCallbackCodec codec = adapterResolver.resolvePort(
        provider().descriptor().adapterId(), "conn-1", ExternalCallbackCodec.class);
    if (adapter instanceof FakeFullProcessEngineAdapter fake) {
      fake.setCallbackEnvelopes(List.of(new ExternalCallbackEnvelope(
          "e-1", "a-1", "c-1", "ext-1", null, "1", null, "APPROVED", "APPROVED", null)));
    }
    ExternalCallbackRequest request = new ExternalCallbackRequest("a-1", "c-1", Map.of("X-Sign", "sig"), "{}");

    Try<List<ExternalCallbackEnvelope>> first = codec.verifyAndParseCallback(request);
    Try<List<ExternalCallbackEnvelope>> second = codec.verifyAndParseCallback(request);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    // 同一请求两次解析结果一致；事件 ID 唯一（inbox 按 eventId 去重只处理一次，见 M9 测试）
    assertThat(second.get()).isEqualTo(first.get());
    assertThat(first.get().stream().map(ExternalCallbackEnvelope::eventId).distinct().count())
        .isEqualTo(first.get().size());
  }

  @Test
  protected void callbackExpiredOrInvalidSignatureRejected() {
    assumeTrue(registered.hasCapability(Capability.CALLBACK));
    ExternalCallbackCodec codec = adapterResolver.resolvePort(
        provider().descriptor().adapterId(), "conn-1", ExternalCallbackCodec.class);
    if (adapter instanceof FakeFullProcessEngineAdapter fake) {
      fake.setFailCallback(true);
    } else if (adapter instanceof FakeResultOnlyEngineAdapter fake) {
      fake.setFailCallback(true);
    }
    ExternalCallbackRequest request = new ExternalCallbackRequest("a-1", "c-1", Map.of("X-Sign", "bad"), "{}");

    Try<List<ExternalCallbackEnvelope>> parsed = codec.verifyAndParseCallback(request);

    assertThat(parsed.isFailure()).isTrue();
  }

  @Test
  void unknownResultCodeNeverAutoCompletes() {
    assumeTrue(supportsActionExecution());
    FakeFullProcessEngineAdapter fake = (FakeFullProcessEngineAdapter) adapter;
    fake.setExecuteOutcome(Try.success(new ExternalActionResult(true, "WEIRD", null, null, null)));
    ExternalStartResult started = fake.start(new ExternalStartCommand(
        "user-1", new ExternalProcessDefinitionRef("ns-1", "def-1", "v3", "hash-3"),
        null, null, null, null, null, null, null)).get();

    Try<ExternalActionResult> executed = fake.execute(new ExternalActionCommand(
        "approve", "approve", "user-1", null, null, null, "ik-1", 1,
        new ExternalInstanceRef(started.externalInstanceId(), "conn-1"),
        new com.ouroboros.workflow.approval.external.ExternalTaskRef(
            "fp-task-1", started.externalInstanceId(), "conn-1")));

    assertThat(executed.isSuccess()).isTrue();
    // 未知结果码不把实例翻到终态（运行时"未知状态不自动结束"规则的适配器侧前提）
    assertThat(fake.instanceStatusOf(started.externalInstanceId())).isEqualTo("RUNNING");
    // 对照：终态结果码才会推进外部引擎状态
    fake.setExecuteOutcome(Try.success(new ExternalActionResult(true, "APPROVED", "approved", null, null)));
    fake.execute(new ExternalActionCommand(
        "approve", "approve", "user-1", null, null, null, "ik-2", 1,
        new ExternalInstanceRef(started.externalInstanceId(), "conn-1"),
        new com.ouroboros.workflow.approval.external.ExternalTaskRef(
            "fp-task-1", started.externalInstanceId(), "conn-1")));
    assertThat(fake.instanceStatusOf(started.externalInstanceId())).isEqualTo("FINISHED");
  }

  @Test
  void unmappedActionFails() {
    assumeTrue(supportsActionExecution());
    FakeFullProcessEngineAdapter fake = (FakeFullProcessEngineAdapter) adapter;

    Try<ExternalActionResult> executed = fake.execute(new ExternalActionCommand(
        "addSign", "addSign", "user-1", null, null, null, "ik-1", 1,
        new ExternalInstanceRef("ext-1", "conn-1"),
        new com.ouroboros.workflow.approval.external.ExternalTaskRef("t-1", "ext-1", "conn-1")));

    assertThat(executed.isFailure()).isTrue();
    assertThat(executed.getCause().getMessage()).contains("EXTERNAL_ACTION_NOT_MAPPED");
  }
}
