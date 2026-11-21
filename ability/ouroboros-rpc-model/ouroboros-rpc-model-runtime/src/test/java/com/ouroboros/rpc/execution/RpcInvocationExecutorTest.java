package com.ouroboros.rpc.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.exception.RPCException;

class RpcInvocationExecutorTest {

  @BeforeEach
  void setUp() {
    RPCModelDefinitionCenter.refresh();
  }

  @Test
  void invoke_runsInterceptorsAroundTheProtocolInvoker() {
    List<String> events = new ArrayList<>();
    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new RecordingProtocolInvoker(events)),
        Arrays.asList(
            new RecordingInterceptor("first", events),
            new RecordingInterceptor("second", events)
        )
    );

    RpcInvocationRequest request = new RpcInvocationRequest(
        "duplicateMock",
        Collections.singletonMap("userId", "42"),
        true,
        "token-fetch",
        Collections.emptyMap()
    );

    RpcInvocationResult result = executor.invoke(request);
    assertTrue(result.isSuccess());
    assertEquals("ok", result.getPayload());
    assertEquals(
        Arrays.asList(
            "before:first",
            "before:second",
            "invoke:duplicateMock:true:token-fetch",
            "after:second",
            "after:first"
        ),
        events
    );
  }

  @Test
  void invoke_rejectsMissingDefinitionsBeforeCallingTransport() {
    AtomicBoolean invoked = new AtomicBoolean(false);
    RpcInvocationExecutor executor = new RpcInvocationExecutor(
        Collections.singletonList(new MissingModelInvoker(invoked)),
        Collections.emptyList()
    );

    RpcInvocationResult result = executor.invoke(new RpcInvocationRequest("missingModel", Collections.emptyMap()));
    assertTrue(result.isFailure());
    assertTrue(result.getError() instanceof RPCException);
    assertFalse(invoked.get());
  }

  private static class RecordingProtocolInvoker implements RpcProtocolInvoker {
    private final List<String> events;

    private RecordingProtocolInvoker(List<String> events) {
      this.events = events;
    }

    @Override
    public boolean supports(com.ouroboros.rpc.RPCModelDefinition definition) {
      return definition != null && "MockType".equalsIgnoreCase(definition.getType());
    }

    @Override
    public RpcInvocationResult invoke(RpcInvocationContext context) {
      events.add("invoke:" + context.getModelName() + ":" + context.isInternalCall() + ":" + context.getPurpose());
      return RpcInvocationResult.success("ok", 12L);
    }
  }

  private static class MissingModelInvoker implements RpcProtocolInvoker {
    private final AtomicBoolean invoked;

    private MissingModelInvoker(AtomicBoolean invoked) {
      this.invoked = invoked;
    }

    @Override
    public boolean supports(com.ouroboros.rpc.RPCModelDefinition definition) {
      return true;
    }

    @Override
    public RpcInvocationResult invoke(RpcInvocationContext context) {
      invoked.set(true);
      return RpcInvocationResult.success("unexpected", 0L);
    }
  }

  private static class RecordingInterceptor implements RpcInvocationInterceptor {
    private final String name;
    private final List<String> events;

    private RecordingInterceptor(String name, List<String> events) {
      this.name = name;
      this.events = events;
    }

    @Override
    public RpcInvocationResult intercept(RpcInvocationContext context, Chain chain) {
      events.add("before:" + name);
      RpcInvocationResult result = chain.proceed(context);
      events.add("after:" + name);
      return result;
    }
  }
}
