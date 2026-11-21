package com.ouroboros.businessmodel.action;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessActionDescriptionControllerTest {
  @Test
  void invokeReturnsBinaryContentWithDownloadHeadersAndClosesItsResource(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var path = java.nio.file.Files.write(directory.resolve("result.tmp"), new byte[]{1, 2, 3});
    var result = com.ouroboros.util.FileResult.temporary(path, "订单.xlsx", "application/octet-stream");
    var runtime = mock(BusinessActionRuntime.class); when(runtime.invoke(any())).thenReturn(result);
    var http = MockMvcBuilders.standaloneSetup(new BusinessActionDescriptionController(runtime)).build();
    http.perform(post("/api/business-model-behaviors/sales.Order/actions/export/invoke")
        .contentType(MediaType.APPLICATION_JSON).content("{\"input\":{}}"))
        .andExpect(status().isOk()).andExpect(content().contentType("application/octet-stream"))
        .andExpect(content().bytes(new byte[]{1, 2, 3})).andExpect(header().string("Content-Length", "3"))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")));
    org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(path));
  }

  @Test
  void typedInvokeCannotOverrideServerIdentityAndReturnsTheOriginalResultEnvelope() throws Exception {
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(List.of("one", "two"));
    var http = MockMvcBuilders.standaloneSetup(new BusinessActionDescriptionController(runtime)).build();
    http.perform(post("/api/business-model-behaviors/sales.Order/actions/submit/invoke")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"modelFullName\":\"other.Model\",\"actionName\":\"delete\",\"expectedContractFingerprint\":\"schema-v1\",\"input\":{\"params\":{\"record\":{\"id\":\"ORD-1\"}},\"data\":{\"comment\":\"ok\"}}}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[0]").value("one")).andExpect(jsonPath("$.data[1]").value("two"));
    verify(runtime).invoke(new BusinessActionInvocation("sales.Order", "submit", com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", "ORD-1")), Map.of("comment", "ok")), "schema-v1"));
  }

  @Test
  void availabilityUsesServerRouteIdentityAndTheUnifiedSideEffectFreeEntry() throws Exception {
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.checkAvailability(any())).thenReturn(new BusinessActionAvailability(
        BusinessActionAvailability.Status.NEEDS_INPUT, List.of("输入不符合 Action 契约")));
    var http = MockMvcBuilders.standaloneSetup(new BusinessActionDescriptionController(runtime)).build();
    http.perform(post("/api/business-model-behaviors/sales.Order/actions/submit/availability")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"modelFullName\":\"sales.Other\",\"actionName\":\"delete\",\"input\":{\"params\":{\"record\":{\"id\":\"ORD-1\"}},\"data\":{\"comment\":\"ok\"}}}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NEEDS_INPUT"));
    verify(runtime).checkAvailability(new BusinessActionInvocation("sales.Order", "submit", com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", "ORD-1")), Map.of("comment", "ok"))));
    verify(runtime, never()).invoke(any());
  }

  @Test
  void authorizedDescriptionsExposeOnlyThePublicSummary() throws Exception {
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.describe("sales.Order")).thenReturn(List.of(new BusinessActionDescription("sales.Order", "rev1",
        "submit", "提交订单", "确认订单", true, false)));
    var http = MockMvcBuilders.standaloneSetup(new BusinessActionDescriptionController(runtime)).build();
    http.perform(get("/api/business-model-behaviors/sales.Order/actions"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("submit"))
        .andExpect(jsonPath("$.data[0].modelRevision").value("rev1"))
        .andExpect(jsonPath("$.data[0].binding").doesNotExist()).andExpect(jsonPath("$.data[0].inputSchema").doesNotExist());
    verify(runtime, never()).invoke(any());
  }
}
