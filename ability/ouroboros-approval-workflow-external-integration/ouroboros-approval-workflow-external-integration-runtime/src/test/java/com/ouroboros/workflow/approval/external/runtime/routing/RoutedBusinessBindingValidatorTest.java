package com.ouroboros.workflow.approval.external.runtime.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCase;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

class RoutedBusinessBindingValidatorTest {
  private final BuiltInApprovalBusinessBindingValidator internal = mock(BuiltInApprovalBusinessBindingValidator.class);
  private final WorkflowEngineMappingService mappings = mock(WorkflowEngineMappingService.class);
  private final WorkflowScopeResolver scope = mock(WorkflowScopeResolver.class);
  private final ExternalInstanceBindingService instances = mock(ExternalInstanceBindingService.class);
  private final ExternalTaskBindingService tasks = mock(ExternalTaskBindingService.class);
  private final ApprovalCaseService cases = mock(ApprovalCaseService.class);
  private final WorkflowEngineAdapterRegistry registry = mock(WorkflowEngineAdapterRegistry.class);
  private final WorkflowEngineMapping mapping = new WorkflowEngineMapping();
  private final ExternalBusinessStateInspection inspection = mock(ExternalBusinessStateInspection.class);
  private RoutedBusinessBindingValidator validator;

  @BeforeEach
  void setUp() {
    validator = validator("internal");
    when(scope.resolve()).thenReturn("tenant");
    mapping.setId("mapping");
    mapping.setVersion(2);
    mapping.setAuthority("EXTERNAL_ENGINE");
    mapping.setAdapterId("engine");
    when(mappings.findByIdForSystem("mapping")).thenReturn(Optional.of(mapping));
  }

  @Test
  void inspectionUsesThePinnedEngineRouteAndNeverFallsBackAfterAnExternalFailure() {
    java.util.function.Supplier<com.ouroboros.workflow.approval.ApprovalInstanceState> local = () -> null;
    when(internal.prepareInstanceInspection("flow", "local-instance")).thenReturn(local);
    org.junit.jupiter.api.Assertions.assertSame(local, validator.prepareInstanceInspection("flow", "local-instance"));
    java.util.function.Supplier<com.ouroboros.workflow.approval.ApprovalInstanceState> remote = () -> null;
    when(inspection.prepare("flow", "instance")).thenReturn(remote);
    org.junit.jupiter.api.Assertions.assertSame(remote, validator.prepareInstanceInspection("flow", "instance"));
    doThrow(new BusinessException("unavailable")).when(inspection).prepare("flow", "instance");
    assertThatThrownBy(() -> validator.prepareInstanceInspection("flow", "instance")).hasMessage("unavailable");
    verify(internal).prepareInstanceInspection("flow", "local-instance");
    org.mockito.Mockito.verifyNoMoreInteractions(internal);
  }

  @Test
  void unmappedAndBuiltInStartsUseBuiltInValidator() {
    when(mappings.findActive("tenant", "flow")).thenReturn(Optional.empty());
    validator.validate("flow", "sales.Order", "1", null, null);
    verify(internal).validate("flow", "sales.Order", "1", null, null);
    mapping.setAuthority("OUROBOROS");
    when(mappings.findActive("tenant", "local")).thenReturn(Optional.of(mapping));
    validator.validate("local", "sales.Order", "1", null, null);
    verify(internal).validate("local", "sales.Order", "1", null, null);
  }

  @Test
  void unconfiguredScopeHonorsUnmappedPolicyAndDoesNotHideAuthorizationFailure() {
    when(scope.resolve()).thenThrow(new WorkflowScopeUnconfiguredException("missing"));
    validator.validate("flow", "sales.Order", "1", null, null);
    verify(internal).validate("flow", "sales.Order", "1", null, null);
    assertThatThrownBy(() -> validator("reject").validate("flow", "sales.Order", "1", null, null))
        .isInstanceOf(BusinessException.class).hasMessageContaining("禁止发起");
    doThrow(new BusinessException("denied")).when(scope).resolve();
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", null, null))
        .hasMessage("denied");
  }

  @Test
  void externalStartUsesExistingMappingForBothIntegrationModes() {
    when(mappings.findActive("tenant", "flow")).thenReturn(Optional.of(mapping));
    when(registry.capabilitiesOf("engine")).thenReturn(java.util.Set.of(com.ouroboros.workflow.approval.external.Capability.EXTERNAL_START));
    when(registry.integrationModeOf("engine")).thenReturn("FULL_PROCESS");
    validator.validate("flow", "sales.Order", "1", null, null);
    verifyNoInteractions(cases);
    verifyNoInteractions(internal);
    when(registry.integrationModeOf("engine")).thenReturn("RESULT_ONLY");
    validator.validate("flow", "sales.Order", "1", null, null);
    verifyNoInteractions(cases, internal);
  }

  @Test
  void externalStartWithoutStarterCapabilityIsRejected() {
    when(mappings.findActive("tenant", "flow")).thenReturn(Optional.of(mapping));
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", null, null))
        .isInstanceOf(BusinessException.class).hasMessageContaining("未提供审批发起能力");
    verifyNoInteractions(cases, internal);
  }

  @Test
  void internalTaskStillUsesEngineOwnedValidation() {
    validator.validate("flow", "sales.Order", "1", "instance", "task");
    verify(internal).validate("flow", "sales.Order", "1", "instance", "task");
  }

  @Test
  void externalTaskUsesPinnedMappingTrustedRecordAndCurrentCase() {
    ExternalInstanceBinding instance = bind();
    var current = new ApprovalCase();
    current.setApprovalCaseId("case");
    current.setSubmissionRound(3);
    current.setActiveExecutionId("instance");
    instance.setApprovalCaseId("case");
    instance.setSubmissionRound(3);
    when(cases.findByBusinessRecord("sales.Order", "1", "flow")).thenReturn(Optional.of(current));
    validator.validate("flow", "sales.Order", "1", "instance", "task");
    verifyNoInteractions(internal);
    current.setSubmissionRound(4);
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", "instance", "task"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("轮次");
  }

  @Test
  void foreignRecordScopeAndMappingCannotPassBindingCheck() {
    bind();
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "2", "instance", "task"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("不匹配");
    when(scope.resolve()).thenReturn("foreign");
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", "instance", "task"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("不匹配");
    mapping.setVersion(3);
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", "instance", "task"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("版本");
    verifyNoInteractions(internal);
  }

  @Test
  void missingInstanceAndMissingCurrentCaseAreRejected() {
    ExternalInstanceBinding instance = bind();
    instance.setApprovalCaseId("case");
    instance.setSubmissionRound(3);
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", "instance", "task"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("轮次");
    when(instances.findByLocalId("instance")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> validator.validate("flow", "sales.Order", "1", "instance", "task"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
  }

  private ExternalInstanceBinding bind() {
    var instance = new ExternalInstanceBinding();
    instance.setLocalInstanceId("instance");
    instance.setMappingId("mapping");
    instance.setMappingVersion(2);
    instance.setScope("tenant");
    instance.setFlowKey("flow");
    instance.setBusinessRecordType("sales.Order");
    instance.setBusinessRecordId("1");
    when(instances.findByLocalId("instance")).thenReturn(Optional.of(instance));
    var task = new ExternalTaskBinding();
    task.setLocalTaskId("task");
    task.setLocalInstanceId("instance");
    task.setScope("tenant");
    task.setMappingId("mapping");
    task.setMappingVersion(2);
    when(tasks.findByLocalId("task")).thenReturn(Optional.of(task));
    return instance;
  }

  private RoutedBusinessBindingValidator validator(String policy) {
    return new RoutedBusinessBindingValidator(internal, mappings, scope, instances, tasks, cases, registry,
        inspection, policy);
  }
}
