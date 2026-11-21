package com.ouroboros.devhelper.command;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.logicflow.debug.LogicFlowDebugRuntimeSupport;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.util.Json;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.ouroboros.logicflow.debug.LogicFlowDebugRuntimeSupport")
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "true")
class LogicFlowCaptureCommands {
  @Bean
  Command probeLogicFlowCapture(LogicFlowDebugRuntimeSupport support) {
    return command("probeLogicFlowCapture", request -> {
      if (support.runtime().knowsCapture(text(request.getParams(), "captureId"))) {
        throw new IllegalStateException("Capture control was lost; begin a new capture after the previous lease ends");
      }
      Object expectedPod = request.getParams().get("expectedPodUid");
      if (expectedPod != null && !expectedPod.equals(System.getenv("OUROBOROS_DEBUG_POD_UID"))) {
        throw new IllegalArgumentException("The selected Kubernetes pod has changed");
      }
      return Map.of("protocol", "2.0", "podUid", System.getenv().getOrDefault("OUROBOROS_DEBUG_POD_UID", ""));
    });
  }

  @Bean
  Command startLogicFlowCapture(LogicFlowDebugRuntimeSupport support) {
    return command("startLogicFlowCapture", request -> {
      var params = request.getParams();
      return support.runtime().start(text(params, "captureId"), target(request), text(params, "flowName"),
          number(params, "version"), remainingLease(params));
    });
  }

  @Bean
  Command renewLogicFlowCapture(LogicFlowDebugRuntimeSupport support) {
    return command("renewLogicFlowCapture", request -> {
      var params = request.getParams();
      return support.runtime().renew(text(params, "captureId"), target(request), number(params, "version"),
          remainingLease(params), number(params, "acknowledgedThrough"));
    });
  }

  @Bean
  Command stopLogicFlowCapture(LogicFlowDebugRuntimeSupport support) {
    return command("stopLogicFlowCapture", request -> {
      var params = request.getParams();
      return support.runtime().stop(text(params, "captureId"), target(request), number(params, "version"),
          number(params, "acknowledgedThrough"));
    });
  }

  @Bean
  Command logicFlowCaptureStatus(LogicFlowDebugRuntimeSupport support) {
    return command("logicFlowCaptureStatus", request -> {
      var params = request.getParams();
      var runtime = support.runtime();
      runtime.acknowledge(text(params, "captureId"), target(request), number(params, "acknowledgedThrough"));
      return runtime.status(text(params, "captureId"));
    });
  }

  private Command command(String name, Function<CommandRequest, Object> action) {
    return new Command() {
      @Override public String getCommand() { return name; }
      @Override public CompletableFuture<CommandResult> execute(CommandRequest request) {
        try {
          return CompletableFuture.completedFuture(new CommandResult(request, action.apply(request)));
        } catch (IllegalArgumentException | IllegalStateException failure) {
          return CompletableFuture.completedFuture(new CommandResult(request).setMessage(failure.getMessage()));
        }
      }
    };
  }

  private FlowDebugTarget target(CommandRequest request) {
    var candidate = Json.tryToBean(FlowDebugTarget.class, Json.tryToJsonString(request.getParams().get("target")).get()).get();
    var actual = HelperRuntimeIdentity.current();
    if (!actual.matches(candidate.helperInstanceId(), candidate.helperStartedAtEpochMillis(), candidate.appIncarnationId())
        || !candidate.appName().equals(request.getAppName()) || !candidate.profile().equals(request.getProfile())
        || !Long.valueOf(candidate.generation()).equals(request.getRuntimeGeneration())) {
      throw new IllegalArgumentException("Debug command is not fenced to this helper process");
    }
    return candidate;
  }

  private String text(Map<String, Object> params, String name) {
    if (!(params.get(name) instanceof String value) || value.isBlank() || value.length() > 256) {
      throw new IllegalArgumentException("Invalid capture parameter: " + name);
    }
    return value;
  }

  private long remainingLease(Map<String, Object> params) {
    return number(params, "leaseUntil") - System.currentTimeMillis();
  }

  private long number(Map<String, Object> params, String name) {
    if (!(params.get(name) instanceof Number value)) {
      throw new IllegalArgumentException("Invalid capture parameter: " + name);
    }
    return value.longValue();
  }
}
