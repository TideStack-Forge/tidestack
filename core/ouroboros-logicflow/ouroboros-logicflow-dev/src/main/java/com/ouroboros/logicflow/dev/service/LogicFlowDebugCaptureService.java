package com.ouroboros.logicflow.dev.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.Capture;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.CreateCapture;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.State;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugRuntime.Operation;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus;

@Service
public class LogicFlowDebugCaptureService {
  private static final long LEASE_MILLIS = 20000;
  private final LogicFlowDebugEventStore store;
  private final ObjectProvider<LogicFlowDebugAccess> access;
  private final ObjectProvider<LogicFlowDebugRuntime> runtime;

  public record CaptureView(String captureId, String runtimeId, String profile, String flowName, String state,
                            long version, long createdAt, long leaseUntil, long expiresAt, long receivedThrough,
                            List<LogicFlowDebugEventStore.Run> runs, String transportError) {}

  public LogicFlowDebugCaptureService(LogicFlowDebugEventStore store, ObjectProvider<LogicFlowDebugAccess> access,
                                     ObjectProvider<LogicFlowDebugRuntime> runtime) {
    this.store = store;
    this.access = access;
    this.runtime = runtime;
  }

  public List<LogicFlowDebugRuntime.Candidate> candidates(String app) {
    return runtime().candidates(app, authorize(app));
  }

  public List<CaptureView> list(String app, String flowName) {
    return recordings(app).stream().filter(capture -> flowName == null || capture.flowName().equals(flowName))
        .map(capture -> view(capture, null)).toList();
  }

  public CaptureView start(String app, String runtimeId, String flowName, String operationId) {
    String owner = authorize(app);
    if (runtimeId == null || runtimeId.isBlank() || runtimeId.length() > 256
        || flowName == null || flowName.isBlank() || flowName.length() > 256) {
      throw new BusinessException(400, "请选择测试容器和逻辑流");
    }
    String captureId = operationId(operationId);
    var existing = store.getCapture(app, captureId);
    Capture capture;
    if (existing.isPresent()) {
      capture = require(app, captureId);
      if (!runtimeId.equals(capture.target().runtimeId()) || !flowName.equals(capture.flowName())) {
        throw new BusinessException(400, "请使用新的操作编号开始另一场调试");
      }
      if (capture.state() != State.STARTING) {
        return view(capture, null);
      }
    } else {
      var target = runtime().probe(app, owner, runtimeId, captureId);
      if (!allowed(app, target.profile(), owner)) {
        throw denied();
      }
      capture = store.tryCreateCapture(new CreateCapture(captureId, operationId, owner, target, flowName, LEASE_MILLIS));
    }
    var status = control(capture, Operation.START);
    if (!status.acceptingRuns()) {
      throw new BusinessException(400, "测试容器未开启采集，请重新开始");
    }
    var latest = require(app, captureId);
    if (latest.state() == State.STARTING) {
      latest = store.updateCapture(app, captureId, latest.version(), State.ACTIVE, remaining(latest));
    }
    return view(latest, status);
  }

  public CaptureView read(String app, String captureId) {
    return view(require(app, captureId), null);
  }

  public CaptureView renew(String app, String captureId) {
    var capture = require(app, captureId);
    if (capture.state() == State.CLOSED || capture.state() == State.STARTING) {
      return view(capture, null);
    }
    if (capture.leaseUntil() <= System.currentTimeMillis()) {
      if (capture.receivedThrough() > 0 || !capture.runs().isEmpty()) {
        return reconcile(capture, Operation.STATUS);
      }
      return view(capture, null);
    }
    if (capture.state() == State.DRAINING || capture.state() == State.SATURATED) {
      return reconcile(capture, Operation.STOP);
    }
    var next = store.updateCapture(app, captureId, capture.version(), State.ACTIVE, LEASE_MILLIS);
    return reconcile(next, Operation.RENEW);
  }

  public CaptureView stop(String app, String captureId) {
    var capture = require(app, captureId);
    if (capture.state() == State.CLOSED || capture.leaseUntil() <= System.currentTimeMillis()) {
      return view(capture, null);
    }
    if (capture.state() != State.DRAINING) {
      // The start response may have been lost; keep its lease fenced while stop reaches the helper.
      capture = store.updateCapture(app, captureId, capture.version(), State.DRAINING, remaining(capture));
    }
    return reconcile(capture, Operation.STOP);
  }

  public Capture require(String app, String id) {
    String owner = authorize(app);
    return store.getCapture(app, id).filter(capture -> owned(app, owner, capture))
        .orElseThrow(() -> new BusinessException(404, "调试记录不存在或已过期"));
  }

  public List<Capture> recordings(String app) {
    String owner = authorize(app);
    return store.listCaptures(app).stream().filter(capture -> owned(app, owner, capture)).toList();
  }

  boolean allowed(String app, String profile, String owner) {
    var policy = access.getIfAvailable();
    return policy != null && policy.canDebug(app, profile, owner);
  }

  private CaptureView reconcile(Capture capture, Operation operation) {
    var status = control(capture, operation);
    String app = capture.target().appName();
    for (var loss : status.losses()) {
      store.declareLoss(app, capture.captureId(), capture.target(), loss);
    }
    var latest = require(app, capture.captureId());
    if (latest.state() == State.ACTIVE && !status.acceptingRuns() && latest.leaseUntil() > System.currentTimeMillis()) {
      latest = store.updateCapture(app, latest.captureId(), latest.version(), State.SATURATED, remaining(latest));
    } else if ((latest.state() == State.DRAINING || latest.state() == State.SATURATED) && latest.leaseUntil() > System.currentTimeMillis() && !status.acceptingRuns()
        && status.activeInvocations() == 0 && latest.receivedThrough() == status.highestSequence()
        && status.acknowledgedThrough() == status.highestSequence()) {
      latest = store.updateCapture(app, capture.captureId(), latest.version(), State.CLOSED, LEASE_MILLIS);
    }
    return view(latest, status);
  }

  private FlowDebugCaptureStatus control(Capture capture, Operation operation) {
    return runtime().control(capture, operation, System.currentTimeMillis());
  }

  private CaptureView view(Capture capture, FlowDebugCaptureStatus status) {
    boolean expired = capture.state() != State.CLOSED && capture.leaseUntil() <= System.currentTimeMillis();
    String state = expired ? "EXPIRED" : capture.state().name();
    if (status != null && "ACTIVE".equals(state) && !status.acceptingRuns()) {
      state = "SATURATED";
    }
    var runs = capture.runs().stream().map(run -> (expired || capture.state() == State.CLOSED)
        && (run.terminalSeq() == null || run.incomplete())
        ? new LogicFlowDebugEventStore.Run(run.runId(), run.flowName(), run.graphId(), run.firstCaptureSeq(), run.startedAt(),
            run.lastEventAt(), run.highestSeq(), run.terminalSeq(), "INCOMPLETE", true)
        : run).toList();
    return new CaptureView(capture.captureId(), capture.target().runtimeId(), capture.target().profile(), capture.flowName(),
        state, capture.version(), capture.createdAt(), capture.leaseUntil(), capture.expiresAt(), capture.receivedThrough(), runs,
        status == null ? null : status.transportError());
  }

  private boolean owned(String app, String owner, Capture capture) {
    return owner.equals(capture.owner()) && allowed(app, capture.target().profile(), owner);
  }

  private String authorize(String app) {
    var policy = access.getIfAvailable();
    if (policy == null) {
      throw denied();
    }
    String owner = policy.currentOwner();
    if (!allowed(app, null, owner)) {
      throw denied();
    }
    return owner;
  }

  private LogicFlowDebugRuntime runtime() {
    var adapter = runtime.getIfAvailable();
    if (adapter == null) {
      throw new BusinessException(400, "当前平台没有装配测试容器调试能力");
    }
    return adapter;
  }

  private static BusinessException denied() {
    return new BusinessException(403, "无权调试此应用");
  }

  private static String operationId(String value) {
    try {
      if (value != null && value.length() == 36) {
        return UUID.fromString(value).toString();
      }
    } catch (IllegalArgumentException malformed) {
      throw new BusinessException(400, "调试操作编号无效", malformed);
    }
    throw new BusinessException(400, "调试操作编号无效");
  }

  private long remaining(Capture capture) {
    return Math.max(1000, capture.leaseUntil() - System.currentTimeMillis());
  }
}
