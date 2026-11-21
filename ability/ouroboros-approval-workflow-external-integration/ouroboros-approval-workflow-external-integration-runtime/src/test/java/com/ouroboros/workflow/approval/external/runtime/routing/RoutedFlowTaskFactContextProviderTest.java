package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.event.FlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutedFlowTaskFactContextProviderTest {
  private final BuiltInFlowTaskFactContextProvider internal = mock(BuiltInFlowTaskFactContextProvider.class);
  private final ExternalInstanceBindingService instances = mock(ExternalInstanceBindingService.class);
  private final ExternalTaskBindingService tasks = mock(ExternalTaskBindingService.class);
  private final WorkflowEngineMappingService mappings = mock(WorkflowEngineMappingService.class);
  private final RoutedFlowTaskFactContextProvider provider = new RoutedFlowTaskFactContextProvider(internal, instances, tasks, mappings);

  @Test
  void ordinaryInternalTasksUseTheExistingProviderAndCapturedFactsAreNotReadAgain() {
    var fact = event();
    provider.capture(fact);
    verify(internal).capture(fact);
    fact.setProjectionVersion(1);
    clearInvocations(internal, instances, tasks);
    provider.capture(fact);
    verifyNoInteractions(internal, instances, tasks);
  }

  @Test
  void partialBusinessIdentityCannotBeTreatedAsAnUnboundApproval() {
    var instance = bind(); instance.setBusinessRecordType(null);
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    instance.setBusinessRecordType("sales.Order"); instance.setBusinessRecordId(" ");
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    verifyNoInteractions(internal);
  }

  @Test
  void externalTasksTakeTheirIdentityFromThePinnedPersistentBindings() {
    var instance = bind();
    var fact = event();
    provider.capture(fact);
    assertEquals("approval", fact.getFlowKey());
    assertEquals(new FlowInstanceEvent.BusinessContext("sales.Order", "42"), fact.getBusinessContext());
    assertEquals("case", fact.getApprovalCaseId());
    assertEquals(3, fact.getSubmissionRound());
    assertEquals("sales.Order.processCallback", fact.getCallbackFlow());
    assertEquals(1, fact.getProjectionVersion());
    instance.setBusinessRecordType(null); instance.setBusinessRecordId(null);
    var unbound = event();
    provider.capture(unbound);
    assertNull(unbound.getBusinessContext());
    verifyNoInteractions(internal);
  }

  @Test
  void absentOrMismatchedBindingsCannotFallBackToFlowable() {
    bind();
    when(tasks.findByLocalId("task")).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    var instance = bind();
    when(instances.findByLocalId("instance")).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    instance = bind();
    instance.setScope("foreign");
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    instance.setScope("tenant"); instance.setMappingVersion(4);
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    verifyNoInteractions(internal);
  }

  @Test
  void conflictingPayloadAndChangedMappingVersionAreRejectedWithoutOverwritingTheClaim() {
    bind();
    var fact = event(); fact.setBusinessContext(new FlowInstanceEvent.BusinessContext("sales.Order", "foreign"));
    assertThrows(BusinessException.class, () -> provider.capture(fact));
    assertEquals("foreign", fact.getBusinessContext().recordId());
    assertNull(fact.getProjectionVersion());
    var mapping = new WorkflowEngineMapping(); mapping.setId("mapping"); mapping.setVersion(4); mapping.setAuthority("EXTERNAL_ENGINE");
    when(mappings.findByIdForSystem("mapping")).thenReturn(Optional.of(mapping));
    assertThrows(BusinessException.class, () -> provider.capture(event()));
    verifyNoInteractions(internal);
  }

  @Test
  void builtInMappingDelegatesThroughTheSameFormalRoute() {
    bind();
    var mapping = new WorkflowEngineMapping(); mapping.setId("mapping"); mapping.setVersion(3); mapping.setAuthority("OUROBOROS");
    when(mappings.findByIdForSystem("mapping")).thenReturn(Optional.of(mapping));
    var fact = event(); provider.capture(fact);
    verify(internal).capture(fact);
  }

  @Test
  void springSelectsTheRoutedProviderWhileKeepingTheBuiltInDelegateUnambiguous() {
    new ApplicationContextRunner().withBean(BuiltInFlowTaskFactContextProvider.class, () -> internal)
        .withBean(ExternalInstanceBindingService.class, () -> instances).withBean(ExternalTaskBindingService.class, () -> tasks)
        .withBean(WorkflowEngineMappingService.class, () -> mappings).withUserConfiguration(RoutedFlowTaskFactContextProvider.class)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(FlowTaskFactContextProvider.class)).isInstanceOf(RoutedFlowTaskFactContextProvider.class);
          assertSame(internal, context.getBean(BuiltInFlowTaskFactContextProvider.class));
        });
  }

  private ExternalInstanceBinding bind() {
    var instance = new ExternalInstanceBinding();
    instance.setLocalInstanceId("instance"); instance.setScope("tenant"); instance.setMappingId("mapping"); instance.setMappingVersion(3);
    instance.setAdapterId("remote"); instance.setConnectionId("connection"); instance.setFlowKey("approval");
    instance.setBusinessRecordType("sales.Order"); instance.setBusinessRecordId("42");
    instance.setApprovalCaseId("case"); instance.setSubmissionRound(3); instance.setCallbackFlow("sales.Order.processCallback");
    var task = new ExternalTaskBinding();
    task.setLocalTaskId("task"); task.setLocalInstanceId("instance"); task.setScope("tenant");
    task.setMappingId("mapping"); task.setMappingVersion(3); task.setAdapterId("remote"); task.setConnectionId("connection");
    when(instances.findByLocalId("instance")).thenReturn(Optional.of(instance));
    when(tasks.findByLocalId("task")).thenReturn(Optional.of(task));
    var mapping = new WorkflowEngineMapping(); mapping.setId("mapping"); mapping.setVersion(3); mapping.setAuthority("EXTERNAL_ENGINE");
    when(mappings.findByIdForSystem("mapping")).thenReturn(Optional.of(mapping));
    return instance;
  }

  private FlowTaskEvent event() {
    var event = new FlowTaskEvent(); event.setInstanceId("instance"); event.setTaskId("task"); return event;
  }
}
