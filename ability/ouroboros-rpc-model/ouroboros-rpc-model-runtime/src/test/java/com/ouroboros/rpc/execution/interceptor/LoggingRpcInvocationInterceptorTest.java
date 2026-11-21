package com.ouroboros.rpc.execution.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.execution.RpcProtocolInvoker;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.util.JsonBag;

@ExtendWith(OutputCaptureExtension.class)
class LoggingRpcInvocationInterceptorTest {

  @BeforeEach
  void setUp() {
    RPCModelDefinitionCenter.refresh();
  }

  @Test
  void invoke_logsMaskedRequestAndResponse(CapturedOutput output) {
    RpcRuntimePolicyService policyService = mock(RpcRuntimePolicyService.class);
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName("duplicateMock");
    policy.setEnabled(true);
    policy.setLoggingEnabled(true);
    policy.setLogRequestBody(true);
    policy.setLogResponseBody(true);
    policy.setMaskHeaders(JsonBag.of(Arrays.asList("Authorization")));
    policy.setMaskFields(JsonBag.of(Arrays.asList("password", "accessToken", "sessionId")));
    when(policyService.getPolicy("duplicateMock")).thenReturn(policy);

    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RpcProtocolInvoker() {
          @Override
          public boolean supports(RPCModelDefinition definition) {
            return definition != null && "MockType".equalsIgnoreCase(definition.getType());
          }

          @Override
          public RpcInvocationResult invoke(RpcInvocationContext context) {
            HttpRPCRequest request = new HttpRPCRequest();
            request.setMethod("POST");
            request.setUrl("https://example.com/token");
            request.header("Authorization", "Bearer secret-token");
            request.query("accessToken", "query-secret");
            request.cookie("sessionId", "cookie-secret");
            request.body("password", "super-secret");
            request.body("username", "demo");
            context.setProtocolRequest(request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("accessToken", "raw-access-token");
            response.put("status", "ok");
            return RpcInvocationResult.success(response, 15L);
          }
        }),
        Arrays.asList(
            new RuntimePolicyRpcInvocationInterceptor(policyService),
            new LoggingRpcInvocationInterceptor()
        )
    );

    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("password", "input-secret");
    parameters.put("name", "demo");

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("duplicateMock", parameters));

    assertTrue(result.isSuccess());
    String logs = output.getOut() + output.getErr();
    assertTrue(logs.contains("duplicateMock"));
    assertTrue(logs.contains("************"));
    assertFalse(logs.contains("secret-token"));
    assertFalse(logs.contains("super-secret"));
    assertFalse(logs.contains("input-secret"));
    assertFalse(logs.contains("raw-access-token"));
    assertFalse(logs.contains("query-secret"));
    assertFalse(logs.contains("cookie-secret"));
  }
}
