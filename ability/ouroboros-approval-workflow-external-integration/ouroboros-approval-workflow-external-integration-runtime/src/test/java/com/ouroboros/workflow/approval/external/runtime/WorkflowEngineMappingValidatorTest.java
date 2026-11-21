package com.ouroboros.workflow.approval.external.runtime;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.DefinitionDiscoveryRequest;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.ExternalFormSnapshot;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionDiscovery;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionFormReader;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionPage;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.ExternalTaskPage;
import com.ouroboros.workflow.approval.external.ExternalTaskQuery;
import com.ouroboros.workflow.approval.external.ExternalTaskReader;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

import io.vavr.control.Try;

/**
 * M3 激活校验规则测试。
 */
class WorkflowEngineMappingValidatorTest {

  private WorkflowEngineAdapterRegistry registry;
  private ConnectionAwareAdapterResolver adapterResolver;
  private WorkflowModelService workflowModelService;
  private com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog connectionConfigProvider;
  private WorkflowEngineMappingValidator validator;

  @BeforeEach
  void setUp() {
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(java.util.List.of()));
    workflowModelService = mock(WorkflowModelService.class);
    connectionConfigProvider = mock(com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.resolve(anyString(), anyString()))
        .thenAnswer(inv -> java.util.Optional.of(ConnectionAwareAdapterResolverTestSupport.connection(
            inv.getArgument(1), inv.getArgument(0))));
    adapterResolver = ConnectionAwareAdapterResolverTestSupport.resolver(registry, connectionConfigProvider);
    validator = new WorkflowEngineMappingValidator(
        registry, adapterResolver, workflowModelService);
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(new com.ouroboros.workflow.approval.manage.entity.WorkflowModel());
    registry.initialize(java.util.List.of(new FullTestProvider()));
  }

  @Test
  void adapterMissingFails() {
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("no-such-adapter");

    assertThat(validator.run(m).isFailure()).isTrue();
    assertThat(validator.run(m).getCause().getMessage()).contains(EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND);
  }

  @Test
  void flowKeyMissingFails() {
    WorkflowEngineMapping m = mapping();
    m.setFlowKey("no-such-flow");

    assertThat(validator.run(m).getCause().getMessage()).contains(EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE);
  }

  @Test
  void invalidAuthorityFails() {
    WorkflowEngineMapping m = mapping();
    m.setAuthority("INVALID");

    assertThat(validator.run(m).isFailure()).isTrue();
  }

  @Test
  void resultOnlyWithActionMappingsFails() {
    registry.initialize(java.util.List.of(new ResultOnlyProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("result-only");
    m.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"agree"}]
        """);

    assertThat(validator.run(m).getCause().getMessage()).contains("RESULT_ONLY");
  }

  @Test
  void nonCanonicalActionFails() {
    WorkflowEngineMapping m = mapping();
    m.setActionMappingsJson("""
        [{"systemAction":"hack","externalAction":"x"}]
        """);

    assertThat(validator.run(m).getCause().getMessage()).contains("规范动作集");
  }

  @Test
  void actionNotDeclaredByAdapterFails() {
    WorkflowEngineMapping m = mapping();
    m.setActionMappingsJson("""
        [{"systemAction":"transfer","externalAction":"redirect"}]
        """);

    // FullTestProvider 只声明 approve/reject
    assertThat(validator.run(m).getCause().getMessage()).contains("适配器未声明");
  }

  @Test
  void resultMappingsMissingTerminalStateFails() {
    WorkflowEngineMapping m = mapping();
    m.setResultMappingsJson("""
        [{"externalStatus":"APPROVED","outcome":"approved"}]
        """);

    assertThat(validator.run(m).getCause().getMessage()).contains("缺少必需终态");
  }

  @Test
  void adapterWithoutCatalogPortFails() {
    registry.initialize(java.util.List.of(new StarterOnlyTestProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("starter-only");

    assertThat(validator.run(m).getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
  }

  @Test
  void unhealthyConnectionFails() {
    registry.initialize(java.util.List.of(new UnhealthyTestProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("unhealthy");

    assertThat(validator.run(m).getCause().getMessage()).contains(EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE);
  }

  @Test
  void validMappingPasses() {
    WorkflowEngineMapping m = mapping();
    m.setResultMappingsJson(resultMappings());

    assertThat(validator.run(m).isSuccess()).isTrue();
  }

  @Test
  void resultOnlyWithoutFinalResultCapabilityFails() {
    registry.initialize(java.util.List.of(new ResultOnlyStarterProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("result-only-starter");

    var failure = validator.run(m).getCause();
    assertThat(failure.getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
    assertThat(failure.getMessage()).contains("EXTERNAL_FINAL_RESULT");
  }

  @Test
  void adapterWithoutTaskReaderPortIsDerivedAsResultOnly() {
    registry.initialize(java.util.List.of(new NoTaskReaderProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("no-task-reader");

    // 没有任务查询端口就不可能进入 FULL_PROCESS；模式不再由映射声明。
    // 该 provider 同时缺少定义校验端口，所以激活仍会因缺能力失败——这正是"能力说话"的体现。
    assertThat(validator.integrationModeOf(m)).isEqualTo("RESULT_ONLY");
    assertThat(validator.run(m).getCause().getMessage())
        .contains(EngineIntegrationErrorCodes.EXTERNAL_CAPABILITY_UNSUPPORTED);
  }

  @Test
  void validMappingPassesAndForwardsNamespace() {
    WorkflowEngineMapping m = mapping();
    m.setResultMappingsJson(resultMappings());
    m.setExternalDefinitionNamespace("corp-a");

    assertThat(validator.run(m).isSuccess()).isTrue();

    assertThat(FullTestProvider.lastValidatedRef).isNotNull();
    assertThat(FullTestProvider.lastValidatedRef.namespace()).isEqualTo("corp-a");
    assertThat(FullTestProvider.lastValidatedRef.definitionId()).isEqualTo("DEF-1");
  }

  @Test
  void adapterDeclaringOnlyResultOnlyModeIsStillDerivedFromCapabilities() {
    registry.initialize(java.util.List.of(new FullTestProvider(), new ModeRestrictiveProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("mode-restrictive");

    // ModeRestrictiveProvider 复用 FullTestAdapter，因此端口能力依然齐备 → 派生 FULL_PROCESS。
    // descriptor 的模式自述不再参与判定（能力才是唯一事实源）。
    assertThat(validator.integrationModeOf(m)).isEqualTo("FULL_PROCESS");
  }

  @Test
  void disabledActionFails() {
    registry.initialize(java.util.List.of(new FullTestProvider(), new ActionRestrictiveProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("action-restrictive");
    m.setActionMappingsJson("""
        [{"systemAction":"rollback","externalAction":"ROLLBACK"}]
        """);

    assertThat(validator.run(m).getCause().getMessage()).contains("已被适配器禁用");
  }

  @Test
  void resultOnlyAdapterRejectsTaskActionMappings() {
    // 接入模式由适配器端口能力派生：该适配器没有任务查询端口，即使 descriptor 声称支持
    // FULL_PROCESS，派生结果仍是 RESULT_ONLY，因此任务动作映射必须被拒绝。
    registry.initialize(java.util.List.of(new ResultOnlyProvider()));
    WorkflowEngineMapping m = mapping();
    m.setAdapterId("result-only");
    m.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);

    assertThat(validator.integrationModeOf(m)).isEqualTo("RESULT_ONLY");
    assertThat(validator.run(m).getCause().getMessage()).contains("RESULT_ONLY 不允许配置任务动作映射");
  }

  @Test
  void fullProcessModeIsDerivedFromTaskAndInstanceQueryPorts() {
    registry.initialize(java.util.List.of(new FullTestProvider()));
    WorkflowEngineMapping m = mapping();
    m.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"APPROVE"}]
        """);

    // FullTestProvider 同时实现实例详情与任务读取端口 → 派生 FULL_PROCESS，任务动作映射合法。
    assertThat(validator.integrationModeOf(m)).isEqualTo("FULL_PROCESS");
    assertThat(validator.run(m).isFailure()).isFalse();
  }

  /** 仅 RESULT_ONLY 的测试 provider（接入模式由端口能力派生）。 */
  static final class ModeRestrictiveProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("mode-restrictive", "mode-restrictive", "1.0", Set.of("approve", "reject"), Set.of(Capability.EXTERNAL_FINAL_RESULT, Capability.POLLING), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new FullTestAdapter();
    }
  }

  /** 动作/连接类型受限的测试 provider（声明 rollback 但禁用；FULL_PROCESS 动作仅 INTERNAL）。 */
  /** 只具备结果回流能力（无任务查询端口）的适配器：派生模式必须是 RESULT_ONLY。 */
  static final class ResultOnlyProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("result-only", "result-only", "1.0", Set.of("approve", "reject"), Set.of(Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ResultOnlyAdapter();
    }
  }

  /** 结果回流适配器：任务查询端口未实现（实例详情与轮询端口仍在，用于结果回流与健康检查）。 */
  static final class ResultOnlyAdapter implements ApprovalWorkflowEngineAdapter {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }
  }

  static final class ActionRestrictiveProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("action-restrictive", "action-restrictive", "1.0", Set.of("approve", "reject", "rollback"), Set.of(Capability.EXTERNAL_FINAL_RESULT, Capability.POLLING), Set.of("rollback"));
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new FullTestAdapter();
    }
  }

  private WorkflowEngineMapping mapping() {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId("m-1");
    m.setScope("app-1");
    m.setFlowKey("flow-a");
    m.setAdapterId("full-test");
    m.setConnectionId("conn-1");
    m.setExternalDefinitionNamespace("corp-a");
    m.setAuthority("EXTERNAL_ENGINE");
    m.setExternalDefinitionId("DEF-1");
    m.setResultMappingsJson(resultMappings());
    return m;
  }

  private String resultMappings() {
    return """
        [
          {"externalStatus":"RUNNING","systemState":"PROCESSING"},
          {"externalStatus":"APPROVED","outcome":"approved"},
          {"externalStatus":"REJECTED","outcome":"rejected"},
          {"externalStatus":"CANCELED","outcome":"cancelled"},
          {"externalStatus":"ERROR","outcome":"failed"}
        ]
        """;
  }

  @Test
  void resultMappingsWithoutErrorTerminalFails() {
    WorkflowEngineMapping m = mapping();
    m.setResultMappingsJson("""
        [
          {"externalStatus":"RUNNING","systemState":"PROCESSING"},
          {"externalStatus":"APPROVED","outcome":"approved"},
          {"externalStatus":"REJECTED","outcome":"rejected"},
          {"externalStatus":"CANCELED","outcome":"cancelled"}
        ]
        """);

    assertThat(validator.run(m).getCause().getMessage()).contains("缺少必需终态: ERROR");
  }

  /** 全功能测试适配器：catalog + starter + 实例/任务读取 + 回调编解码，健康。 */
  static final class FullTestProvider implements ApprovalWorkflowEngineAdapterProvider {
    private static ExternalProcessDefinitionRef lastValidatedRef;

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("full-test", "full", "1.0", Set.of("approve", "reject"), Set.of(Capability.EXTERNAL_FINAL_RESULT, Capability.POLLING), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new FullTestAdapter();
    }
  }

  static final class FullTestAdapter
      implements ApprovalWorkflowEngineAdapter,
          ExternalProcessDefinitionDiscovery,
          ExternalProcessDefinitionValidator,
          ExternalProcessDefinitionFormReader,
          ExternalWorkflowStarter,
          ExternalInstanceReader,
          ExternalTaskReader,
          ExternalCallbackCodec {

    private ExternalProcessDefinitionRef lastValidatedRef;

    ExternalProcessDefinitionRef lastValidatedRef() {
      return lastValidatedRef;
    }

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalProcessDefinitionPage> discoverDefinitions(DefinitionDiscoveryRequest request) {
      return Try.success(new ExternalProcessDefinitionPage(java.util.List.of(), false));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalProcessDefinition> validateDefinition(
        ExternalProcessDefinitionRef definition) {
      this.lastValidatedRef = definition;
      FullTestProvider.lastValidatedRef = definition;
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalProcessDefinition(
          definition.definitionId(), null, "DEF", null));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalFormSchema> getDefinitionForm(
        ExternalProcessDefinitionRef definition) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalFormSchema(java.util.List.of(), Map.of()));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalStartResult> start(
        com.ouroboros.workflow.approval.external.ExternalStartCommand command) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalStartResult("ext-1", null, null));
    }

    @Override
    public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
      return Try.success(new ExternalInstanceSnapshot(
          instance.externalInstanceId(), "RUNNING", null, null, null, Map.of()));
    }

    @Override
    public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
      return Try.success(new ExternalInstancePage(java.util.List.of(), null));
    }

    @Override
    public Try<ExternalTaskPage> queryTasks(ExternalTaskQuery query) {
      return Try.success(new ExternalTaskPage(java.util.List.of(), false, null));
    }

    @Override
    public Try<ExternalTaskSnapshot> getTask(ExternalTaskRef task) {
      return Try.success(new ExternalTaskSnapshot(task.externalTaskId(), null, null, "RUNNING", null, Map.of()));
    }

    @Override
    public Try<ExternalFormSnapshot> getTaskForm(ExternalTaskRef task) {
      return Try.success(new ExternalFormSnapshot(Map.of()));
    }

    @Override
    public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
      return Try.success(java.util.List.of());
    }
  }

  /** 只有 starter 端口的测试适配器（无 catalog，无结果能力声明）。 */
  static final class ResultOnlyStarterProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("result-only-starter", "starter", "1.0", Set.of(), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new StarterOnlyAdapter();
    }
  }

  /** starter + 声明结果能力但缺任务读取端口的测试适配器（FULL_PROCESS 能力校验用）。 */
  static final class NoTaskReaderProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("no-task-reader", "no-task", "1.0", Set.of("approve"), Set.of(Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<ExternalHealth> health() {
          return Try.success(new ExternalHealth(true, "ok"));
        }
      };
    }
  }

  /** starter 端口适配器（catalog 端口缺失场景与结果能力缺失场景共用）。 */
  static final class StarterOnlyAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalWorkflowStarter {

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalStartResult> start(
        com.ouroboros.workflow.approval.external.ExternalStartCommand command) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalStartResult("ext-1", null, null));
    }
  }

  /** 只有 starter 端口的测试适配器（无 catalog，但声明了结果能力，用于 catalog 缺失场景）。 */
  static final class StarterOnlyTestProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("starter-only", "starter", "1.0", Set.of(), Set.of(Capability.EXTERNAL_FINAL_RESULT), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new StarterOnlyAdapter();
    }
  }

  /** 健康检查失败的测试适配器（实现 catalog + starter 端口以到达健康检查）。 */
  static final class UnhealthyTestProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("unhealthy", "unhealthy", "1.0", Set.of(), Set.of(Capability.EXTERNAL_FINAL_RESULT, Capability.POLLING), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new UnhealthyTestAdapter();
    }
  }

  static final class UnhealthyTestAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalProcessDefinitionDiscovery,
          ExternalProcessDefinitionValidator, ExternalProcessDefinitionFormReader, ExternalWorkflowStarter,
          ExternalInstanceReader {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(false, "down"));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot> getInstance(
        com.ouroboros.workflow.approval.external.ExternalInstanceRef instance) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot(
          instance.externalInstanceId(), "RUNNING", null, null, null, Map.of()));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalInstancePage> queryChangedInstances(
        com.ouroboros.workflow.approval.external.ExternalInstanceQuery query) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalInstancePage(java.util.List.of(), null));
    }

    @Override
    public Try<ExternalProcessDefinitionPage> discoverDefinitions(DefinitionDiscoveryRequest request) {
      return Try.success(new ExternalProcessDefinitionPage(java.util.List.of(), false));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalProcessDefinition> validateDefinition(
        ExternalProcessDefinitionRef definition) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalProcessDefinition(
          definition.definitionId(), null, "DEF", null));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalFormSchema> getDefinitionForm(
        ExternalProcessDefinitionRef definition) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalFormSchema(java.util.List.of(), Map.of()));
    }

    @Override
    public Try<com.ouroboros.workflow.approval.external.ExternalStartResult> start(
        com.ouroboros.workflow.approval.external.ExternalStartCommand command) {
      return Try.success(new com.ouroboros.workflow.approval.external.ExternalStartResult("ext-1", null, null));
    }
  }

  /**
   * authority=OUROBOROS 表示本地内置引擎执行，动作由内置引擎完成，没有外部动作可映射。
   *
   * <p>此前这里要求动作形如 {@code CUSTOM_SHORTCUT:agree|refuse}——那是镜像快捷回调的约定，
   * 而处理该回调的代码已随镜像投影删除，约束沦为"接受一份永不执行的配置"。改为直接拒绝。
   */
  @Test
  void ouroborosAuthorityRejectsActionMappings() {
    registry.initialize(java.util.List.of(new FullTestProvider()));
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("built-in-with-actions");
    mapping.setVersion(1);
    mapping.setScope("default");
    mapping.setFlowKey("flow-a");
    mapping.setAdapterId("full-test");
    mapping.setConnectionId("conn-1");
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"CUSTOM_SHORTCUT:agree"}]
        """);
    // registry 已在 setUp 里初始化过 FullTestProvider；这里只跑校验本身
    Try<Void> result = validator.run(mapping);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("不允许配置动作映射");
  }
}
