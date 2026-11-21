package com.ouroboros.rpc.dev.service.testing;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.execution.RpcExecutionException;
import com.ouroboros.rpc.execution.RpcInvocationService;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.util.Json;

@Service
public class RpcModelTestService {
  private final RpcModelTestSnapshotReader snapshots;
  private final RpcInvocationService executor;
  private final ConfigurationService configuration;
  private final Environment environment;

  RpcModelTestService(RpcModelTestSnapshotReader snapshots, RpcInvocationService executor,
                      ConfigurationService configuration, Environment environment) {
    this.snapshots = snapshots; this.executor = executor; this.configuration = configuration; this.environment = environment;
  }

  public RpcModelTestInit initialize(String name, Map<String, String> configValues) {
    var redactor = new RpcModelTestRedactor(previewLimit());
    var view = new RpcModelTestConfiguration(configValues, configuration, environment, redactor);
    var snapshot = snapshots.read(name, view::get);
    var inputs = RpcModelTestInputs.discover(snapshot);
    redactor.credential(snapshot.model(name).getRequest());
    boolean enabled = snapshot.model(name).isEnabled();
    if (enabled) validateSupport(snapshot);
    var missing = missingConfiguration(inputs, view);
    return new RpcModelTestInit(redactor.sanitize(Json.toMap(Json.toJsonString(snapshot.model(name)))), snapshot.modelHash(),
        snapshot.revision(), "development-platform", inputs.variables(), inputs.configItems(view::defaultSource),
        enabled, enabled && missing.isEmpty(), missing,
        !enabled ? "模型已禁用，请启用并保存后测试" : missing.isEmpty() ? null : "请填写缺少的配置项", maxDuration());
  }

  public RpcModelTestResult execute(String name, RpcModelTestRequest request) {
    long started = System.nanoTime();
    var testId = UUID.randomUUID().toString();
    var redactor = new RpcModelTestRedactor(previewLimit());
    RpcModelTestResources resources = null;
    try {
      var view = new RpcModelTestConfiguration(request.configValues(), configuration, environment, redactor);
      var snapshot = snapshots.read(name, view::get);
      if (!snapshot.revision().equals(request.revision())) {
        throw new RpcModelTestFailure("RESOURCE", "RESOURCE_CHANGED", "模型或认证依赖已变化，请重新加载", name, null);
      }
      var inputs = RpcModelTestInputs.discover(snapshot);
      inputs.validate(request);
      inputs.variables().stream().filter(RpcModelTestInputs.Variable::sensitive)
          .forEach(variable -> redactor.addSecret(request.variables().get(variable.name())));
      var missing = missingConfiguration(inputs, view);
      if (!missing.isEmpty()) {
        throw new RpcModelTestFailure("INPUT", "INPUT_INVALID", "缺少必需配置项", null, missing.getFirst());
      }
      resources = new RpcModelTestResources(snapshot, executor, view::get, redactor,
          started + maxDuration() * 1_000_000L, requestLimit());
      var result = executor.invoke(new RpcInvocationRequest(name, request.variables(), false, "developer-test", Map.of()), resources);
      var error = result.isSuccess() ? null : result.getError() instanceof RpcExecutionException failure
          ? RpcModelTestRedactor.error(failure.getStage()) : RpcModelTestRedactor.error("TRANSPORT");
      return output(testId, request.revision(), result.isSuccess(), started, resources, redactor, error);
    } catch (RpcModelTestFailure failure) {
      return output(testId, request.revision(), false, started, resources, redactor, failure.toError());
    }
  }

  private RpcModelTestResult output(String testId, String revision, boolean success, long started,
      RpcModelTestResources resources, RpcModelTestRedactor redactor, RpcModelTestResult.Error error) {
    var exchanges = resources == null ? List.<com.ouroboros.rpc.execution.RpcHttpExchange>of() : resources.exchanges();
    // Process token responses first so extracted secret values also mask the main request.
    var authentication = exchanges.stream().filter(exchange -> "token-fetch".equals(exchange.purpose()))
        .map(redactor::exchange).toList();
    var main = exchanges.stream().filter(exchange -> !"token-fetch".equals(exchange.purpose()))
        .map(redactor::exchange).toList();
    var last = main.isEmpty() ? null : main.getLast();
    return new RpcModelTestResult(testId, revision, success, Math.max(0L, (System.nanoTime() - started) / 1_000_000L),
        main.size(), last == null ? null : last.request(), last == null ? null : last.response(), error, main, authentication);
  }

  private List<String> missingConfiguration(RpcModelTestInputs inputs, RpcModelTestConfiguration view) {
    return inputs.configKeys().stream().filter(key -> view.get(key) == null).sorted().toList();
  }

  private void validateSupport(RpcModelTestSnapshot snapshot) {
    for (String name : snapshot.modelContents().keySet()) {
      var model = snapshot.model(name);
      if (!model.isEnabled() || !RPCModelDefinitionCenter.supports(model)) {
        throw new RpcModelTestFailure("RESOURCE", "RESOURCE_UNAVAILABLE", "RPC 模型已禁用或类型不支持", name, null);
      }
    }
    for (String name : snapshot.credentialContents().keySet()) {
      if (!RPCAuthCredentialCenter.supports(snapshot.credential(name))) {
        throw new RpcModelTestFailure("AUTH", "AUTHENTICATION_FAILED", "认证类型不支持", name, null);
      }
    }
  }

  private long maxDuration() { return positiveSetting("max-duration-ms", 120000); }
  private int previewLimit() { return positiveSetting("max-preview-bytes", 262144); }
  private int requestLimit() { return positiveSetting("max-request-bytes", 1048576); }

  private int positiveSetting(String key, int fallback) {
    int value = environment.getProperty("ouroboros.rpc.developer-test." + key, Integer.class, fallback);
    if (value <= 0) { throw new IllegalStateException("RPC 测试系统限制必须大于零"); }
    return value;
  }
}
