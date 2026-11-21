package com.ouroboros.workflow.approval.external.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.definition.startrequirements.BuiltInStartRequirementsResolver;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.startrequirements.ExternalStartRequirementsParser;

import io.vavr.control.Try;

/**
 * M22-T3 发起需求路由测试：无映射/镜像 → 委托内置；EXTERNAL_ENGINE → 外部解析；解析失败透传。
 */
class RoutedFlowDefinitionServiceTest {

  private BuiltInFlowDefinitionService internalDefinition;
  private WorkflowEngineMappingService mappingService;
  private WorkflowScopeResolver scopeResolver;
  private BuiltInStartRequirementsResolver internalResolver;
  private ExternalStartRequirementsParser externalParser;
  private RoutedFlowDefinitionService routed;

  @BeforeEach
  void setUp() {
    internalDefinition = mock(BuiltInFlowDefinitionService.class);
    ObjectProvider<BuiltInFlowDefinitionService> internalProvider = providerOf(internalDefinition);
    mappingService = mock(WorkflowEngineMappingService.class);
    scopeResolver = mock(WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    internalResolver = mock(BuiltInStartRequirementsResolver.class);
    ObjectProvider<BuiltInStartRequirementsResolver> resolverProvider = providerOf(internalResolver);
    externalParser = mock(ExternalStartRequirementsParser.class);
    routed = new RoutedFlowDefinitionService(
        internalProvider, mappingService, scopeResolver, resolverProvider, externalParser);
  }

  @Test
  void canonicalDefinitionReadPreservesTheInternalGraphInsteadOfRoundTrippingThroughV1() {
    var internal = mock(BuiltInFlowDefinitionService.class);
    var graph = new com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition(); graph.setFormatVersion(2);
    when(internal.getStructuredFlowDefinition("flow-a")).thenReturn(Try.success(graph));
    var service = new RoutedFlowDefinitionService(providerOf(internal), mappingService, scopeResolver, providerOf(internalResolver), externalParser);
    assertThat(service.getStructuredFlowDefinition("flow-a").get()).isSameAs(graph);
    verify(internal, never()).getFlowDefinition("flow-a");
  }

  @Test
  void unmappedFlowDelegatesToInternalResolver() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.empty());
    when(internalResolver.getStartRequirements("flow-a", null))
        .thenReturn(Try.success(StartRequirements.none(true)));

    Try<StartRequirements> result = routed.getStartRequirements("flow-a", null);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().ccEnabled()).isTrue();
    verify(internalResolver).getStartRequirements("flow-a", null);
    verify(externalParser, never()).parse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void mirrorMappingDelegatesToInternalResolver() {
    WorkflowEngineMapping mirror = new WorkflowEngineMapping();
    mirror.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mirror));
    when(internalResolver.getStartRequirements("flow-a", null))
        .thenReturn(Try.success(StartRequirements.none(true)));

    Try<StartRequirements> result = routed.getStartRequirements("flow-a", null);

    assertThat(result.isSuccess()).isTrue();
    verify(internalResolver).getStartRequirements("flow-a", null);
    verify(externalParser, never()).parse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void externalMappingDelegatesToExternalParser() {
    WorkflowEngineMapping external = new WorkflowEngineMapping();
    external.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    external.setId("m-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(external));
    when(externalParser.parse(external, null))
        .thenReturn(Try.success(StartRequirements.none(false)));

    Try<StartRequirements> result = routed.getStartRequirements("flow-a", null);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().requirements()).isEmpty();
    verify(externalParser).parse(external, null);
    verify(internalResolver, never()).getStartRequirements(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void externalParseFailureIsPropagated() {
    WorkflowEngineMapping external = new WorkflowEngineMapping();
    external.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    external.setId("m-1");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(external));
    when(externalParser.parse(external, null))
        .thenReturn(Try.failure(new BusinessException("[WORKFLOW_MAPPING_NOT_ACTIVE] startOptionsJson 解析失败: m-1")));

    Try<StartRequirements> result = routed.getStartRequirements("flow-a", null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains("startOptionsJson 解析失败");
    verify(internalResolver, never()).getStartRequirements(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void externalDefinitionAndNodesComeFromThePlatformModelWithoutAnInternalDeployment() {
    var models = externalModels();
    var model = new com.ouroboros.workflow.approval.manage.entity.WorkflowModel();
    model.setJson("""
        {"name":"flow-a","title":"采购审批","enabled":true,
         "form":{"formType":"default","formId":"purchase-form"},
         "nodes":[{"id":"start","name":"发起","type":"StartNode"},
                  {"id":"end","name":"结束","type":"EndNode"}],
         "edges":[{"id":"edge","type":"SEQUENCE","sourceId":"start","targetId":"end"}]}
        """);
    when(models.getByPrimaryKey("flow-a")).thenReturn(model);

    var definition = routed.getStructuredFlowDefinition("flow-a").get();
    assertThat(definition.getId()).isEqualTo("flow-a");
    assertThat(definition.getNodes()).extracting(node -> node.getId()).containsExactly("start", "end");
    assertThat(routed.getFlowDefinition("flow-a").get().getName()).isEqualTo("采购审批");
    // 与内置服务使用同一导航器，表单节点列表只包含发起和审批节点。
    assertThat(routed.getFlowNodes("flow-a")).extracting(node -> node.getId()).containsExactly("start");
    verify(internalDefinition, never()).getStructuredFlowDefinition("flow-a");
    verify(internalDefinition, never()).getFlowDefinition("flow-a");
    verify(internalDefinition, never()).getFlowNodes("flow-a");
  }

  @Test
  void missingOrDisabledExternalDefinitionFailsWithoutFallingBackToTheInternalEngine() {
    var models = externalModels();
    assertThat(routed.getStructuredFlowDefinition("flow-a").getCause().getMessage()).contains("不存在");
    var disabled = new com.ouroboros.workflow.approval.manage.entity.WorkflowModel();
    disabled.setJson("""
        {"name":"flow-a","title":"采购审批","enabled":false,
         "form":{"formType":"default","formId":"purchase-form"},
         "nodes":[{"id":"start","type":"StartNode"},{"id":"end","type":"EndNode"}],
         "edges":[{"id":"edge","type":"SEQUENCE","sourceId":"start","targetId":"end"}]}
        """);
    when(models.getByPrimaryKey("flow-a")).thenReturn(disabled);
    assertThat(routed.getStructuredFlowDefinition("flow-a").getCause().getMessage()).contains("已禁用");
    verify(internalDefinition, never()).getStructuredFlowDefinition("flow-a");
  }

  @Test
  void forbiddenScopeNeverFallsBackToInternalDefinitionReads() {
    when(scopeResolver.resolve()).thenThrow(new BusinessException("scope forbidden"));
    assertThat(routed.getStructuredFlowDefinition("flow-a").getCause().getMessage()).contains("scope forbidden");
    verify(internalDefinition, never()).getStructuredFlowDefinition("flow-a");
  }

  private com.ouroboros.workflow.approval.manage.service.WorkflowModelService externalModels() {
    var mapping = new WorkflowEngineMapping();
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping));
    var models = mock(com.ouroboros.workflow.approval.manage.service.WorkflowModelService.class);
    org.springframework.test.util.ReflectionTestUtils.setField(routed, "workflowModels", models);
    return models;
  }

  private <T> ObjectProvider<T> providerOf(T instance) {
    return new ObjectProvider<T>() {
      @Override
      public T getObject() {
        return instance;
      }

      @Override
      public T getObject(Object... args) {
        return instance;
      }

      @Override
      public T getIfAvailable() {
        return instance;
      }

      @Override
      public T getIfUnique() {
        return instance;
      }

      @Override
      public Stream<T> stream() {
        return Stream.of(instance);
      }
    };
  }
}
