package com.ouroboros.rpc.runtime.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryItem;
import com.ouroboros.rpc.runtime.service.impl.support.InMemoryTypedDataModel;
import com.ouroboros.util.JsonBag;

class RpcRuntimeRegistryServiceImplTest {

  @BeforeEach
  void setUp() {
    RPCModelDefinitionCenter.refresh();
  }

  @Test
  void listModels_mergesDefinitionPolicyAndStatusIntoOneView() {
    PolicyDataModel policyDataModel = new PolicyDataModel();
    policyDataModel.put(policy("duplicateMock", false, 800L, 1));
    policyDataModel.put(policy("openApi", true, 1500L, 3));
    StatusDataModel statusDataModel = new StatusDataModel();
    statusDataModel.put(status("duplicateMock", "UNAVAILABLE", 4L, 7L, 2L));

    RpcRuntimeRegistryServiceImpl service = new RpcRuntimeRegistryServiceImpl(
        policyService(policyDataModel),
        statusService(statusDataModel)
    );

    List<RpcRuntimeRegistryItem> items = service.listModels();

    assertEquals(Arrays.asList("duplicateMock", "openApi"), items.stream()
        .map(RpcRuntimeRegistryItem::getModelName)
        .collect(Collectors.toList()));

    RpcRuntimeRegistryItem duplicate = items.get(0);
    assertEquals("MockType", duplicate.getType());
    assertFalse(duplicate.getPolicy().getEnabled());
    assertEquals(Long.valueOf(800L), duplicate.getPolicy().getTimeoutOverrideMs());
    assertEquals("UNAVAILABLE", duplicate.getStatus().getAvailability());
    assertEquals(Long.valueOf(7L), duplicate.getStatus().getFailureCount());
    assertEquals(Long.valueOf(4L), duplicate.getStatus().getConsecutiveFailureCount());
    assertEquals(Long.valueOf(2L), duplicate.getStatus().getAvgLatencyMs());

    RpcRuntimeRegistryItem openApi = items.get(1);
    assertEquals("metadata", openApi.getGroup());
    assertEquals("OpenApi", openApi.getType());
    assertTrue(openApi.getPolicy().getEnabled());
    assertEquals(Long.valueOf(1500L), openApi.getPolicy().getTimeoutOverrideMs());
    assertEquals("HEALTHY", openApi.getStatus().getAvailability());
    assertEquals(Long.valueOf(0L), openApi.getStatus().getSuccessCount());
    assertEquals(Long.valueOf(0L), openApi.getStatus().getFailureCount());
  }

  @Test
  void listModels_toleratesInvalidPersistedPolicyOverrides() {
    PolicyDataModel policyDataModel = new PolicyDataModel();
    policyDataModel.put(invalidPolicy("duplicateMock"));
    StatusDataModel statusDataModel = new StatusDataModel();

    RpcRuntimeRegistryServiceImpl service = new RpcRuntimeRegistryServiceImpl(
        policyService(policyDataModel),
        statusService(statusDataModel)
    );

    List<RpcRuntimeRegistryItem> items = service.listModels();

    RpcRuntimeRegistryItem duplicate = items.stream()
        .filter(item -> "duplicateMock".equals(item.getModelName()))
        .findFirst()
        .orElseThrow(AssertionError::new);
    assertFalse(duplicate.getPolicy().getEnabled());
    assertTrue(duplicate.getPolicy().getHasNormalizationWarnings());
    assertEquals(3, duplicate.getPolicy().getNormalizationWarnings().size());
    assertNull(duplicate.getPolicy().getForcedAvailabilityStatus());
    assertNull(duplicate.getPolicy().getTimeoutOverrideMs());
    assertNull(duplicate.getPolicy().getRetryOverride());
  }

  @Test
  void resetStatus_preservesModelIdentityButClearsFailureCounters() {
    StatusDataModel dataModel = new StatusDataModel();
    dataModel.put(status("openApi", "UNAVAILABLE", 5L, 9L, 18L));
    RpcRuntimeStatusServiceImpl service = new RpcRuntimeStatusServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimeStatusModel> getDataModel() {
        return dataModel;
      }
    };

    RpcRuntimeStatusModel reset = service.resetStatus("openApi");

    assertEquals("openApi", reset.getModelName());
    assertEquals("HEALTHY", reset.getAvailability());
    assertEquals(Long.valueOf(0L), reset.getConsecutiveFailureCount());
    assertEquals(Long.valueOf(0L), reset.getFailureCount());
    assertEquals(Long.valueOf(0L), reset.getSuccessCount());
    assertEquals(Long.valueOf(0L), reset.getAvgLatencyMs());
    assertNull(reset.getLastFailureAt());
    assertNull(reset.getLastFailureReason());
    assertEquals(reset, dataModel.peek("openApi"));
  }

  private static RpcRuntimePolicyServiceImpl policyService(PolicyDataModel dataModel) {
    return new RpcRuntimePolicyServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
        return dataModel;
      }
    };
  }

  private static RpcRuntimeStatusServiceImpl statusService(StatusDataModel dataModel) {
    return new RpcRuntimeStatusServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimeStatusModel> getDataModel() {
        return dataModel;
      }
    };
  }

  private static RpcRuntimePolicyModel policy(String modelName, boolean enabled, Long timeoutOverrideMs, Integer retryOverride) {
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName(modelName);
    policy.setEnabled(enabled);
    policy.setLoggingEnabled(enabled);
    policy.setLogRequestBody(false);
    policy.setLogResponseBody(false);
    policy.setMaskHeaders(JsonBag.of(Arrays.asList("Authorization")));
    policy.setMaskFields(JsonBag.of(Arrays.asList("password")));
    policy.setMetricsEnabled(enabled);
    policy.setAvailabilityEnabled(enabled);
    policy.setTimeoutOverrideMs(timeoutOverrideMs);
    policy.setRetryOverride(retryOverride);
    return policy;
  }

  private static RpcRuntimePolicyModel invalidPolicy(String modelName) {
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName(modelName);
    policy.setEnabled(false);
    policy.setLoggingEnabled(true);
    policy.setMetricsEnabled(true);
    policy.setAvailabilityEnabled(true);
    policy.setForcedAvailabilityStatus("DEGRADED");
    policy.setTimeoutOverrideMs(0L);
    policy.setRetryOverride(-1);
    return policy;
  }

  private static RpcRuntimeStatusModel status(
      String modelName,
      String availability,
      Long consecutiveFailureCount,
      Long failureCount,
      Long avgLatencyMs
  ) {
    RpcRuntimeStatusModel status = new RpcRuntimeStatusModel();
    status.setModelName(modelName);
    status.setAvailability(availability);
    status.setLastInvokeAt(LocalDateTime.now());
    status.setLastFailureAt(LocalDateTime.now());
    status.setLastFailureReason("boom");
    status.setConsecutiveFailureCount(consecutiveFailureCount);
    status.setSuccessCount(1L);
    status.setFailureCount(failureCount);
    status.setAvgLatencyMs(avgLatencyMs);
    return status;
  }

  private static class PolicyDataModel extends InMemoryTypedDataModel<RpcRuntimePolicyModel> {
    @Override
    protected String getId(RpcRuntimePolicyModel model) {
      return model.getModelName();
    }
  }

  private static class StatusDataModel extends InMemoryTypedDataModel<RpcRuntimeStatusModel> {
    @Override
    protected String getId(RpcRuntimeStatusModel model) {
      return model.getModelName();
    }
  }
}
