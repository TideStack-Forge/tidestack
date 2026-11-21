package com.ouroboros.rpc.runtime.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.service.impl.support.InMemoryTypedDataModel;
import com.ouroboros.util.JsonBag;

class RpcRuntimePolicyServiceImplTest {

  @Test
  void getPolicy_returnsCodeDefaultsWhenNoPolicyRowExists() {
    PolicyDataModel dataModel = new PolicyDataModel();
    RpcRuntimePolicyServiceImpl service = new RpcRuntimePolicyServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
        return dataModel;
      }
    };

    RpcRuntimePolicyModel policy = service.getPolicy("openApi");

    assertEquals("openApi", policy.getModelName());
    assertTrue(policy.getEnabled());
    assertFalse(policy.getLoggingEnabled());
    assertFalse(policy.getLogRequestBody());
    assertFalse(policy.getLogResponseBody());
    assertFalse(policy.getMetricsEnabled());
    assertFalse(policy.getAvailabilityEnabled());
    assertNull(policy.getMaskHeaders());
    assertNull(policy.getMaskFields());
    assertNull(policy.getForcedAvailabilityStatus());
    assertNull(policy.getTimeoutOverrideMs());
    assertNull(policy.getRetryOverride());
    assertNull(policy.getUpdatedAt());
    assertNull(dataModel.peek("openApi"));
  }

  @Test
  void updatePolicy_persistsMaskingAndOverrideFields() {
    PolicyDataModel dataModel = new PolicyDataModel();
    RpcRuntimePolicyServiceImpl service = new RpcRuntimePolicyServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
        return dataModel;
      }
    };

    RpcRuntimePolicyModel update = new RpcRuntimePolicyModel();
    update.setLoggingEnabled(true);
    update.setLogRequestBody(true);
    update.setLogResponseBody(true);
    update.setMaskHeaders(JsonBag.of(Arrays.asList("Authorization", "X-Api-Key")));
    update.setMaskFields(JsonBag.of(Arrays.asList("password", "accessToken")));
    update.setMetricsEnabled(true);
    update.setAvailabilityEnabled(true);
    update.setForcedAvailabilityStatus("UNAVAILABLE");
    update.setTimeoutOverrideMs(1500L);
    update.setRetryOverride(3);
    update.setUpdatedBy("tester");

    RpcRuntimePolicyModel saved = service.updatePolicy("openApi", update);
    RpcRuntimePolicyModel persisted = dataModel.peek("openApi");

    assertNotNull(saved.getUpdatedAt());
    assertNotNull(persisted);
    assertEquals("openApi", persisted.getModelName());
    assertTrue(persisted.getLoggingEnabled());
    assertTrue(persisted.getLogRequestBody());
    assertTrue(persisted.getLogResponseBody());
    assertEquals(JsonBag.of(Arrays.asList("Authorization", "X-Api-Key")), persisted.getMaskHeaders());
    assertEquals(JsonBag.of(Arrays.asList("password", "accessToken")), persisted.getMaskFields());
    assertTrue(persisted.getMetricsEnabled());
    assertTrue(persisted.getAvailabilityEnabled());
    assertEquals("UNAVAILABLE", persisted.getForcedAvailabilityStatus());
    assertEquals(Long.valueOf(1500L), persisted.getTimeoutOverrideMs());
    assertEquals(Integer.valueOf(3), persisted.getRetryOverride());
    assertEquals("tester", persisted.getUpdatedBy());
    assertEquals(persisted.getUpdatedAt(), saved.getUpdatedAt());
  }

  @Test
  void getPolicy_toleratesInvalidPersistedRuntimeOverrides() {
    PolicyDataModel dataModel = new PolicyDataModel();
    dataModel.put(invalidPolicy("openApi"));
    RpcRuntimePolicyServiceImpl service = new RpcRuntimePolicyServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
        return dataModel;
      }
    };

    RpcRuntimePolicyModel policy = service.getPolicy("openApi");

    assertEquals("openApi", policy.getModelName());
    assertFalse(policy.getEnabled());
    assertTrue(policy.getLoggingEnabled());
    assertEquals(JsonBag.of(Arrays.asList("Authorization")), policy.getMaskHeaders());
    assertEquals(JsonBag.of(Arrays.asList("password")), policy.getMaskFields());
    assertTrue(policy.getMetricsEnabled());
    assertTrue(policy.getAvailabilityEnabled());
    assertNull(policy.getForcedAvailabilityStatus());
    assertNull(policy.getTimeoutOverrideMs());
    assertNull(policy.getRetryOverride());
    assertTrue(policy.getHasNormalizationWarnings());
    assertEquals(3, policy.getNormalizationWarnings().size());
    assertTrue(policy.getNormalizationWarnings().stream().anyMatch(item -> item.contains("forcedAvailabilityStatus")));
    assertTrue(policy.getNormalizationWarnings().stream().anyMatch(item -> item.contains("timeoutOverrideMs")));
    assertTrue(policy.getNormalizationWarnings().stream().anyMatch(item -> item.contains("retryOverride")));
  }

  @Test
  void updatePolicy_rejectsInvalidRuntimeOverrides() {
    PolicyDataModel dataModel = new PolicyDataModel();
    RpcRuntimePolicyServiceImpl service = new RpcRuntimePolicyServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
        return dataModel;
      }
    };

    RpcRuntimePolicyModel update = new RpcRuntimePolicyModel();
    update.setTimeoutOverrideMs(0L);
    update.setRetryOverride(-1);

    ValidationException exception = assertThrows(ValidationException.class, () -> service.updatePolicy("openApi", update));

    assertTrue(exception.getMessage().contains("timeoutOverrideMs"));
    assertNull(dataModel.peek("openApi"));
  }

  @Test
  void updatePolicy_rejectsUnknownForcedAvailabilityStatus() {
    PolicyDataModel dataModel = new PolicyDataModel();
    RpcRuntimePolicyServiceImpl service = new RpcRuntimePolicyServiceImpl() {
      @Override
      protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
        return dataModel;
      }
    };

    RpcRuntimePolicyModel update = new RpcRuntimePolicyModel();
    update.setForcedAvailabilityStatus("DEGRADED");

    ValidationException exception = assertThrows(ValidationException.class, () -> service.updatePolicy("openApi", update));

    assertTrue(exception.getMessage().contains("forcedAvailabilityStatus"));
    assertNull(dataModel.peek("openApi"));
  }

  private static class PolicyDataModel extends InMemoryTypedDataModel<RpcRuntimePolicyModel> {
    @Override
    protected String getId(RpcRuntimePolicyModel model) {
      return model.getModelName();
    }
  }

  private static RpcRuntimePolicyModel invalidPolicy(String modelName) {
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName(modelName);
    policy.setEnabled(false);
    policy.setLoggingEnabled(true);
    policy.setMaskHeaders(JsonBag.of(Arrays.asList("Authorization")));
    policy.setMaskFields(JsonBag.of(Arrays.asList("password")));
    policy.setMetricsEnabled(true);
    policy.setAvailabilityEnabled(true);
    policy.setForcedAvailabilityStatus("DEGRADED");
    policy.setTimeoutOverrideMs(0L);
    policy.setRetryOverride(-1);
    return policy;
  }
}
