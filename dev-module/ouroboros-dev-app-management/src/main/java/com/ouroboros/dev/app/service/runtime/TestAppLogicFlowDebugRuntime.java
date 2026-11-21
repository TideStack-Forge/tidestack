package com.ouroboros.dev.app.service.runtime;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.Capture;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugRuntime;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.util.Json;

@Component
public class TestAppLogicFlowDebugRuntime implements LogicFlowDebugRuntime {
  private static final Logger logger = LoggerFactory.getLogger(TestAppLogicFlowDebugRuntime.class);
  private static final long COMMAND_TIMEOUT_MILLIS = 2500;
  private final DevRuntimeService runtimes;
  private final DevHelperCommandService commands;
  private final AuthenticationPrincipalTestAppProfileResolver profiles;

  public TestAppLogicFlowDebugRuntime(DevRuntimeService runtimes, DevHelperCommandService commands,
                                     AuthenticationPrincipalTestAppProfileResolver profiles) {
    this.runtimes = runtimes;
    this.commands = commands;
    this.profiles = profiles;
  }

  @Override
  public List<Candidate> candidates(String appName, String owner) {
    return running(appName, profiles.resolveProfileId(owner)).stream()
        .filter(instance -> owns(instance, owner))
        .map(instance -> new Candidate(instance.getRuntimeId(), instance.getProfile(), instance.getBackendType().name(),
            instance.getBackendMetadata().getOrDefault("containerName", instance.getRuntimeId()), supported(instance)))
        .toList();
  }

  @Override
  public FlowDebugTarget probe(String appName, String owner, String runtimeId, String captureId) {
    String profile = profiles.resolveProfileId(owner);
    var selected = running(appName, profile).stream().filter(runtime -> runtimeId.equals(runtime.getRuntimeId()))
        .findFirst().orElseThrow(() -> new BusinessException("选定的测试容器已停止或已更换，请重新选择"));
    if (!owns(selected, owner)) {
      throw new AuthorizationDeniedException("无权调试该测试容器");
    }
    if (!supported(selected)) {
      throw new BusinessException("测试容器尚未就绪或未安装当前版本的调试助手，请更新并重建");
    }
    var params = new LinkedHashMap<String, Object>();
    params.put("profile", profile);
    params.put("captureId", captureId);
    String incarnation = selected.getBackendMetadata().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
    params.put(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY, incarnation);
    String podUid = selected.getBackendMetadata().get("debugPodUid");
    if (podUid != null) {
      params.put("expectedPodUid", podUid);
    }
    var reply = request(appName, "probeLogicFlowCapture", params);
    if (!(reply.getData() instanceof Map<?, ?> data) || !"2.0".equals(data.get("protocol"))
        || !incarnation.equals(reply.getAppIncarnationId()) || !profile.equals(reply.getProfile())
        || !appName.equals(reply.getAppName()) || (podUid != null && !podUid.equals(data.get("podUid")))
        || reply.getHelperInstanceId() == null || reply.getHelperStartedAtEpochMillis() == null) {
      throw new BusinessException("测试容器的调试助手身份或协议不匹配");
    }
    return new FlowDebugTarget(appName, profile, runtimeId, generation(selected), reply.getHelperInstanceId(),
        reply.getHelperStartedAtEpochMillis(), incarnation);
  }

  @Override
  public FlowDebugCaptureStatus control(Capture capture, Operation operation, long now) {
    var target = capture.target();
    var params = new LinkedHashMap<String, Object>();
    params.put("profile", target.profile());
    params.put("runtimeGeneration", target.generation());
    params.put(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY, target.helperInstanceId());
    params.put(MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY, target.helperStartedAtEpochMillis());
    params.put(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY, target.appIncarnationId());
    params.put("captureId", capture.captureId());
    params.put("target", target);
    params.put("flowName", capture.flowName());
    params.put("version", capture.version());
    params.put("leaseUntil", capture.leaseUntil());
    params.put("acknowledgedThrough", capture.receivedThrough());
    if (operation == Operation.START || operation == Operation.RENEW) {
      if (capture.leaseUntil() - now < 1000) {
        throw new BusinessException("调试采集租约已到期，请重新开始");
      }
    }
    String command = switch (operation) {
      case START -> "startLogicFlowCapture";
      case RENEW -> "renewLogicFlowCapture";
      case STOP -> "stopLogicFlowCapture";
      case STATUS -> "logicFlowCaptureStatus";
    };
    var result = request(target.appName(), command, params);
    var status = Json.tryToJsonString(result.getData()).flatMap(json -> Json.tryToBean(FlowDebugCaptureStatus.class, json))
        .getOrElseThrow(failure -> new BusinessException("测试容器返回了无效的调试状态", failure));
    if (status == null || !capture.captureId().equals(status.captureId()) || !target.equals(status.target())) {
      throw new BusinessException("测试容器返回的调试状态与会话不匹配");
    }
    return status;
  }

  private List<RuntimeInstance> running(String appName, String profile) {
    return Arrays.stream(BackendType.values()).map(backend -> runtimes.inspect(RuntimeIdentityKey.of(
            appName, profile, RuntimeKind.TEST_APP, backend)))
        .filter(result -> {
          if (result.isFailure()) {
            logger.debug("Could not inspect test runtime for online debugging: {}", result.getCause().toString());
          }
          return result.isSuccess();
        })
        .map(result -> result.get())
        .filter(Objects::nonNull)
        .filter(instance -> instance.getStatus() == RuntimeStatus.RUNNING && instance.getRuntimeKind() == RuntimeKind.TEST_APP
            && appName.equals(instance.getAppName()) && profile.equals(instance.getProfile()) && instance.getRuntimeId() != null)
        .toList();
  }

  private boolean owns(RuntimeInstance instance, String owner) {
    return owner.equals(instance.getBackendMetadata().get("OUROBOROS_DEBUG_OWNER"));
  }

  private boolean supported(RuntimeInstance instance) {
    return instance.getBackendMetadata().containsKey(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV)
        && (instance.getBackendType() != BackendType.KUBERNETES || instance.getBackendMetadata().containsKey("debugPodUid"));
  }

  private long generation(RuntimeInstance instance) {
    return instance.getRuntimeGeneration() == null ? 0 : instance.getRuntimeGeneration();
  }

  private CommandResult request(String appName, String command, Map<String, Object> params) {
    CommandResult result;
    try {
      result = commands.exec(appName, command, params, COMMAND_TIMEOUT_MILLIS).join();
    } catch (CompletionException failure) {
      throw new BusinessException("测试容器的调试助手未响应，请检查容器和消息队列", failure.getCause());
    } catch (RuntimeException failure) {
      throw new BusinessException("无法发送调试命令，请检查消息队列", failure);
    }
    if (!result.getSuccess()) {
      throw new BusinessException(result.getMessage() == null ? "测试容器拒绝了调试命令" : result.getMessage());
    }
    return result;
  }
}
