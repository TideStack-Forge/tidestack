package com.ouroboros.devhelper.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.logicflow.debug.LogicFlowDebugRuntimeSupport;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.mq.builder.MQProducerBuilder;

class LogicFlowCaptureCommandsTest {
  private final MQBridge bridge = mock(MQBridge.class);

  private ApplicationContextRunner context() {
    when(bridge.bindingProducer(anyString())).thenAnswer(call -> new MQProducerBuilder(call.getArgument(0), props -> mock(MQProducer.class)));
    return new ApplicationContextRunner()
        .withUserConfiguration(LogicFlowCaptureCommands.class)
        .withBean(LogicFlowDebugRuntimeSupport.class, () -> new LogicFlowDebugRuntimeSupport(bridge, "crm", true));
  }

  @Test
  void onlyTestAppInstallsCaptureCommandsAndCommandsUseTheActualRuntime() {
    context().withPropertyValues("ouroboros.dev-helper.test-app=true").run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBeansOfType(Command.class)).hasSize(5);
      var identity = HelperRuntimeIdentity.current();
      var target = new FlowDebugTarget("crm", "alice", "runtime", 1, identity.getInstanceId(),
          identity.getStartedAtEpochMillis(), identity.getAppIncarnationId());
      var params = new LinkedHashMap<String, Object>();
      params.put("captureId", "capture");
      params.put("target", target);
      params.put("flowName", "flow");
      params.put("version", 1);
      params.put("leaseUntil", System.currentTimeMillis() + 10000);
      var request = new CommandRequest();
      request.setAppName("crm");
      request.setProfile("alice");
      request.setRuntimeGeneration(1L);
      request.setParams(params);
      assertThat(context.getBean("probeLogicFlowCapture", Command.class).execute(request).join().getSuccess()).isTrue();
      var start = context.getBean("startLogicFlowCapture", Command.class).execute(request).join();
      assertThat(start.getSuccess()).isTrue();
      var runtime = context.getBean(LogicFlowDebugRuntimeSupport.class).runtime();
      assertThat(runtime.status("capture").acceptingRuns()).isTrue();
      assertThat(context.getBean("probeLogicFlowCapture", Command.class).execute(request).join().getSuccess()).isFalse();

      params.put("version", 2);
      params.put("acknowledgedThrough", 0);
      assertThat(context.getBean("renewLogicFlowCapture", Command.class).execute(request).join().getSuccess()).isTrue();
      assertThat(context.getBean("logicFlowCaptureStatus", Command.class).execute(request).join().getSuccess()).isTrue();
      params.put("version", 3);
      var stop = context.getBean("stopLogicFlowCapture", Command.class).execute(request).join();
      assertThat(stop.getSuccess()).isTrue();
      assertThat(runtime.status("capture").acceptingRuns()).isFalse();
    });
  }

  @Test
  void metadataHelperDoesNotInstallTestAppCaptureCommands() {
    context().run(context -> assertThat(context.getBeansOfType(Command.class)).isEmpty());
  }

  @Test
  void wrongProcessAndExpiredCommandCannotStartCapture() {
    context().withPropertyValues("ouroboros.dev-helper.test-app=true").run(context -> {
      var request = new CommandRequest();
      request.setAppName("crm");
      request.setProfile("alice");
      request.setRuntimeGeneration(1L);
      request.setParams(Map.of("captureId", "capture", "flowName", "flow", "version", 1,
          "leaseUntil", System.currentTimeMillis() + 10000,
          "target", new FlowDebugTarget("crm", "alice", "runtime", 1, "other-helper", 100, "other-app")));
      assertThat(context.getBean("startLogicFlowCapture", Command.class).execute(request).join().getSuccess()).isFalse();
    });
  }

  @Test
  void expiredLeaseAndMalformedControlAreRejectedWithoutStartingCapture() {
    context().withPropertyValues("ouroboros.dev-helper.test-app=true").run(context -> {
      var identity = HelperRuntimeIdentity.current();
      var params = new LinkedHashMap<String, Object>();
      params.put("captureId", "capture"); params.put("flowName", "flow"); params.put("version", 1);
      params.put("leaseUntil", System.currentTimeMillis() - 1000);
      params.put("target", new FlowDebugTarget("crm", "alice", "runtime", 1, identity.getInstanceId(),
          identity.getStartedAtEpochMillis(), identity.getAppIncarnationId()));
      var request = new CommandRequest(); request.setAppName("crm"); request.setProfile("alice");
      request.setRuntimeGeneration(1L); request.setParams(params);
      var start = context.getBean("startLogicFlowCapture", Command.class);
      assertThat(start.execute(request).join().getSuccess()).isFalse();
      params.put("leaseUntil", System.currentTimeMillis() + 10000); params.put("version", "invalid");
      assertThat(start.execute(request).join().getSuccess()).isFalse();
      params.put("version", 1); params.put("flowName", " ");
      assertThat(start.execute(request).join().getSuccess()).isFalse();
      params.put("expectedPodUid", "replacement-pod");
      assertThat(context.getBean("probeLogicFlowCapture", Command.class).execute(request).join().getSuccess()).isFalse();
      assertThat(context.getBean(LogicFlowDebugRuntimeSupport.class).runtime().knowsCapture("capture")).isFalse();
    });
  }
}
