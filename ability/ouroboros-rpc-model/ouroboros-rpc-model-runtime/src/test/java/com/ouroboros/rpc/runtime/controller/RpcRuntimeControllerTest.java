package com.ouroboros.rpc.runtime.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeOperationService;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryItem;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryService;
import com.ouroboros.rpc.runtime.service.impl.RpcRuntimeStatusServiceImpl;
import com.ouroboros.util.JsonBag;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

class RpcRuntimeControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  private RpcRuntimeRegistryService registryService;
  private RpcRuntimePolicyService policyService;
  private RpcRuntimeOperationService operationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    registryService = mock(RpcRuntimeRegistryService.class);
    policyService = mock(RpcRuntimePolicyService.class);
    operationService = mock(RpcRuntimeOperationService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(
        new RpcRuntimeController(registryService, policyService, operationService)
    )
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();
  }

  @Test
  void list_returnsDefinitionPolicyAndStatusRows() throws Exception {
    when(registryService.listModels()).thenReturn(Arrays.asList(
        item("duplicateMock", false, "UNAVAILABLE", Collections.singletonList("timeoutOverrideMs 非法值已忽略")),
        item("openApi", true, RpcRuntimeStatusServiceImpl.AVAILABILITY_HEALTHY)
    ));

    mockMvc.perform(get("/api/sys/rpc-runtime"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[0].modelName").value("duplicateMock"))
        .andExpect(jsonPath("$.data.items[0].policy.enabled").value(false))
        .andExpect(jsonPath("$.data.items[0].policy.hasNormalizationWarnings").value(true))
        .andExpect(jsonPath("$.data.items[0].policy.normalizationWarnings[0]").value("timeoutOverrideMs 非法值已忽略"))
        .andExpect(jsonPath("$.data.items[0].status.availability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data.items[1].modelName").value("openApi"))
        .andExpect(jsonPath("$.data.items[1].group").value("钉钉/通讯录"))
        .andExpect(jsonPath("$.data.items[1].policy.hasNormalizationWarnings").value(false))
        .andExpect(jsonPath("$.data.items[1].status.availability").value("HEALTHY"));
  }

  @Test
  void get_returnsPolicyWarningsWhenPresent() throws Exception {
    when(registryService.getModel("duplicateMock")).thenReturn(Optional.of(
        item("duplicateMock", false, "UNAVAILABLE", Collections.singletonList("forcedAvailabilityStatus 非法值已忽略"))
    ));

    mockMvc.perform(get("/api/sys/rpc-runtime/duplicateMock"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.modelName").value("duplicateMock"))
        .andExpect(jsonPath("$.data.policy.hasNormalizationWarnings").value(true))
        .andExpect(jsonPath("$.data.policy.normalizationWarnings[0]").value("forcedAvailabilityStatus 非法值已忽略"));
  }

  @Test
  void updatePolicy_writesEditableFieldsOnly() throws Exception {
    when(registryService.getModel("openApi")).thenReturn(Optional.of(item("openApi", true, "HEALTHY")));
    when(policyService.updatePolicy(eq("openApi"), any(RpcRuntimePolicyModel.class)))
        .thenAnswer(invocation -> {
          RpcRuntimePolicyModel source = invocation.getArgument(1);
          RpcRuntimePolicyModel response = new RpcRuntimePolicyModel();
          response.setModelName("openApi");
          response.setEnabled(source.getEnabled());
          response.setLoggingEnabled(source.getLoggingEnabled());
          response.setLogRequestBody(source.getLogRequestBody());
          response.setLogResponseBody(source.getLogResponseBody());
          response.setMaskHeaders(source.getMaskHeaders());
          response.setMaskFields(source.getMaskFields());
          response.setMetricsEnabled(source.getMetricsEnabled());
          response.setAvailabilityEnabled(source.getAvailabilityEnabled());
          response.setForcedAvailabilityStatus(source.getForcedAvailabilityStatus());
          response.setTimeoutOverrideMs(source.getTimeoutOverrideMs());
          response.setRetryOverride(source.getRetryOverride());
          return response;
        });

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("enabled", false);
    body.put("loggingEnabled", true);
    body.put("logRequestBody", true);
    body.put("logResponseBody", false);
    body.put("maskHeaders", Arrays.asList("Authorization"));
    body.put("maskFields", Arrays.asList("password"));
    body.put("metricsEnabled", true);
    body.put("availabilityEnabled", true);
    body.put("forcedAvailabilityStatus", "UNAVAILABLE");
    body.put("timeoutOverrideMs", 2500L);
    body.put("retryOverride", 4);

    mockMvc.perform(put("/api/sys/rpc-runtime/openApi/policy")
            .contentType("application/json")
            .content(objectMapper.writeValueAsBytes(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.modelName").value("openApi"))
        .andExpect(jsonPath("$.data.loggingEnabled").value(true))
        .andExpect(jsonPath("$.data.timeoutOverrideMs").value(2500));

    ArgumentCaptor<RpcRuntimePolicyModel> captor = ArgumentCaptor.forClass(RpcRuntimePolicyModel.class);
    verify(policyService).updatePolicy(eq("openApi"), captor.capture());
    RpcRuntimePolicyModel captured = captor.getValue();
    assertNull(captured.getModelName());
    assertNull(captured.getTenantId());
    assertFalse(captured.getEnabled());
    assertTrue(captured.getLoggingEnabled());
    assertTrue(captured.getLogRequestBody());
    assertFalse(captured.getLogResponseBody());
    assertEquals(JsonBag.of(Arrays.asList("Authorization")), captured.getMaskHeaders());
    assertEquals(JsonBag.of(Arrays.asList("password")), captured.getMaskFields());
    assertTrue(captured.getMetricsEnabled());
    assertTrue(captured.getAvailabilityEnabled());
    assertEquals("UNAVAILABLE", captured.getForcedAvailabilityStatus());
    assertEquals(Long.valueOf(2500L), captured.getTimeoutOverrideMs());
    assertEquals(Integer.valueOf(4), captured.getRetryOverride());
  }

  @Test
  void healthCheck_endpoint_returnsOperationResult() throws Exception {
    when(registryService.getModel("openApi")).thenReturn(Optional.of(item("openApi", true, "HEALTHY")));
    when(operationService.healthCheck("openApi")).thenReturn(RpcInvocationResult.success(Collections.singletonMap("status", "ok"), 18L));

    mockMvc.perform(post("/api/sys/rpc-runtime/openApi/actions/health-check"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.modelName").value("openApi"))
        .andExpect(jsonPath("$.data.success").value(true))
        .andExpect(jsonPath("$.data.durationMs").value(18));
  }

  @Test
  void resetStatus_endpoint_returnsResetSnapshot() throws Exception {
    when(registryService.getModel("openApi")).thenReturn(Optional.of(item("openApi", true, "HEALTHY")));
    RpcRuntimeStatusModel reset = new RpcRuntimeStatusModel();
    reset.setModelName("openApi");
    reset.setAvailability("HEALTHY");
    reset.setFailureCount(0L);
    when(operationService.resetStatus("openApi")).thenReturn(reset);

    mockMvc.perform(post("/api/sys/rpc-runtime/openApi/actions/reset-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.modelName").value("openApi"))
        .andExpect(jsonPath("$.data.failureCount").value(0));
  }

  @Test
  void repairPolicy_endpoint_returnsRepairedPolicy() throws Exception {
    when(registryService.getModel("openApi")).thenReturn(Optional.of(item("openApi", true, "HEALTHY")));
    RpcRuntimePolicyModel repaired = new RpcRuntimePolicyModel();
    repaired.setModelName("openApi");
    repaired.setEnabled(true);
    repaired.setNormalizationWarnings(Collections.emptyList());
    when(operationService.repairPolicy("openApi")).thenReturn(repaired);

    mockMvc.perform(post("/api/sys/rpc-runtime/openApi/actions/repair-policy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.modelName").value("openApi"))
        .andExpect(jsonPath("$.data.hasNormalizationWarnings").value(false));
  }

  @Test
  void testInvoke_endpoint_wrapsParametersIntoInvocationRequest() throws Exception {
    when(registryService.getModel("openApi")).thenReturn(Optional.of(item("openApi", true, "HEALTHY")));
    when(operationService.testInvoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(Collections.singletonMap("status", "ok"), 23L));

    Map<String, Object> body = Collections.<String, Object>singletonMap(
        "parameters",
        Collections.<String, Object>singletonMap("userId", "42")
    );

    mockMvc.perform(post("/api/sys/rpc-runtime/openApi/actions/test-invoke")
            .contentType("application/json")
            .content(objectMapper.writeValueAsBytes(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.success").value(true))
        .andExpect(jsonPath("$.data.payload.status").value("ok"));

    ArgumentCaptor<RpcInvocationRequest> captor = ArgumentCaptor.forClass(RpcInvocationRequest.class);
    verify(operationService).testInvoke(captor.capture());
    RpcInvocationRequest request = captor.getValue();
    assertEquals("openApi", request.getModelName());
    assertEquals(Collections.singletonMap("userId", "42"), request.getParameters());
    assertFalse(request.isInternalCall());
    assertEquals("manual-test", request.getPurpose());
  }

  @Test
  void unknownModel_returnsNotFoundPayload() throws Exception {
    when(registryService.getModel("missingModel")).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/sys/rpc-runtime/missingModel"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("指定模型不存在"));
  }

  @Test
  void missingModelWriteEndpoints_shouldReturnHttp404AndSkipOperations() throws Exception {
    when(registryService.getModel("missingModel")).thenReturn(Optional.empty());

    mockMvc.perform(put("/api/sys/rpc-runtime/missingModel/policy")
            .contentType("application/json")
            .content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    mockMvc.perform(post("/api/sys/rpc-runtime/missingModel/actions/health-check"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    mockMvc.perform(post("/api/sys/rpc-runtime/missingModel/actions/reset-status"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    mockMvc.perform(post("/api/sys/rpc-runtime/missingModel/actions/repair-policy"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    mockMvc.perform(post("/api/sys/rpc-runtime/missingModel/actions/test-invoke")
            .contentType("application/json")
            .content("{\"parameters\":{\"userId\":\"42\"}}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    verifyNoInteractions(policyService, operationService);
  }

  private RpcRuntimeRegistryItem item(String modelName, boolean enabled, String availability) {
    return item(modelName, enabled, availability, Collections.emptyList());
  }

  private RpcRuntimeRegistryItem item(String modelName, boolean enabled, String availability, java.util.List<String> normalizationWarnings) {
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName(modelName);
    policy.setEnabled(enabled);
    policy.setLoggingEnabled(enabled);
    policy.setNormalizationWarnings(normalizationWarnings);

    RpcRuntimeStatusModel status = new RpcRuntimeStatusModel();
    status.setModelName(modelName);
    status.setAvailability(availability);

    return new RpcRuntimeRegistryItem(
        modelName,
        modelName + " title",
        "钉钉/通讯录",
        "OpenApi",
        "accessToken",
        Collections.<String, Object>singletonMap("timeout", 3000),
        policy,
        status
    );
  }
}
