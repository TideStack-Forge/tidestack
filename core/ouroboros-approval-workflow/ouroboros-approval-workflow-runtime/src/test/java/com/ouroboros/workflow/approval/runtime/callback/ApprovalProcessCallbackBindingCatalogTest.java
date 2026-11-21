package com.ouroboros.workflow.approval.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.callback.CallbackBindingProvider;
import com.ouroboros.workflow.approval.runtime.testutil.TestObjectProviders;

/**
 * M23-T1 回调绑定目录测试：注册表命中（含元数据）、未注册降级约定式、重建覆盖旧值。
 */
class ApprovalProcessCallbackBindingCatalogTest {

  @Test
  void explicitFlowResolvesOnlyUniqueFormalBindingsAndDoesNotGuessFromAFlowName() {
    var catalog = new ApprovalProcessCallbackBindingCatalog(flowProvider("a.processCallback"), emptyProvider());
    assertThat(catalog.resolveByCallbackFlow("a.processCallback")).isEmpty();
    var binding = new CallbackBinding("flow", "a.processCallback", "a", "status", Map.of());
    catalog.register(binding);
    assertThat(catalog.resolveByCallbackFlow("a.processCallback")).contains(binding);
    assertThat(catalog.resolveByCallbackFlow("unknown")).isEmpty();
    catalog.register(new CallbackBinding("flow", "a.processCallback", "other-model", null, null));
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> catalog.resolveByCallbackFlow("a.processCallback"));
  }

  @Test
  void registeredBindingIsResolvedWithMetadata() {
    FlowModelProvider flowModelProvider = flowProvider("awf-expense.processCallback");
    ApprovalProcessCallbackBindingCatalog catalog =
        new ApprovalProcessCallbackBindingCatalog(flowModelProvider, emptyProvider());
    catalog.register(new CallbackBinding("FLOW_1", "awf-expense.processCallback", "awf-expense",
        "status", Map.of("approved", Map.of("code", "20"))));

    Optional<CallbackBinding> resolved = catalog.resolveByRecordType("awf-expense");

    assertThat(resolved).isPresent();
    assertThat(resolved.get().callbackFlow()).isEqualTo("awf-expense.processCallback");
    assertThat(resolved.get().flowKey()).isEqualTo("FLOW_1");
    assertThat(resolved.get().statusField()).isEqualTo("status");
    assertThat(resolved.get().statusMap()).containsKey("approved");
  }

  @Test
  void unregisteredRecordTypeFallsBackToConvention() {
    FlowModelProvider flowModelProvider = flowProvider("order-approval.processCallback");
    ApprovalProcessCallbackBindingCatalog catalog =
        new ApprovalProcessCallbackBindingCatalog(flowModelProvider, emptyProvider());

    Optional<CallbackBinding> resolved = catalog.resolveByRecordType("order-approval");

    assertThat(resolved).isPresent();
    assertThat(resolved.get().callbackFlow()).isEqualTo("order-approval.processCallback");
    assertThat(resolved.get().statusField()).isNull();
    assertThat(resolved.get().statusMap()).isNull();
  }

  @Test
  void resolveFailsWhenConventionFlowMissing() {
    FlowModelProvider flowModelProvider = mock(FlowModelProvider.class);
    when(flowModelProvider.getFlowModel("order-approval.processCallback"))
        .thenReturn(Optional.empty());
    ApprovalProcessCallbackBindingCatalog catalog =
        new ApprovalProcessCallbackBindingCatalog(flowModelProvider, emptyProvider());

    assertThat(catalog.resolveByRecordType("order-approval")).isEmpty();
  }

  @Test
  void registerAllRebuildsRegistryReplacingOldValues() {
    FlowModelProvider flowModelProvider = flowProvider(
        "a.processCallback", "b.processCallback");
    ApprovalProcessCallbackBindingCatalog catalog =
        new ApprovalProcessCallbackBindingCatalog(flowModelProvider, emptyProvider());
    catalog.register(new CallbackBinding("F1", "a.processCallback", "a", "statusA", Map.of()));

    catalog.registerAll(List.of(
        new CallbackBinding("F2", "a.processCallback", "a", "statusA2", Map.of()),
        new CallbackBinding("F3", "b.processCallback", "b", "statusB", Map.of())));

    assertThat(catalog.resolveByRecordType("a").get().statusField()).isEqualTo("statusA2");
    assertThat(catalog.resolveByRecordType("b").get().statusField()).isEqualTo("statusB");
  }

  @Test
  void metadataRefreshEventRebuildsFromProviders() {
    FlowModelProvider flowModelProvider = flowProvider("a.processCallback");
    CallbackBindingProvider provider = () -> List.of(
        new CallbackBinding("F1", "a.processCallback", "a", "statusA", Map.of()));
    ApprovalProcessCallbackBindingCatalog catalog = new ApprovalProcessCallbackBindingCatalog(
        flowModelProvider, providerOf(provider));

    catalog.onOuroborosMetadataRefreshed(mock(com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent.class));

    assertThat(catalog.resolveByRecordType("a")).isPresent();
    assertThat(catalog.resolveByRecordType("a").get().statusField()).isEqualTo("statusA");
  }

  private FlowModelProvider flowProvider(String... flowNames) {
    FlowModelProvider flowModelProvider = mock(FlowModelProvider.class);
    for (String flowName : flowNames) {
      when(flowModelProvider.getFlowModel(flowName))
          .thenReturn(Optional.of(mock(FlowModel.class)));
    }
    return flowModelProvider;
  }

  private ObjectProvider<CallbackBindingProvider> emptyProvider() {
    return TestObjectProviders.of(() -> List.of());
  }

  private ObjectProvider<CallbackBindingProvider> providerOf(CallbackBindingProvider provider) {
    return TestObjectProviders.of(provider);
  }
}
