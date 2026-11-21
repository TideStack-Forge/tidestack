package com.ouroboros.logicflow.dev.service;

import java.util.List;

import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.Capture;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;

/** Test runtime discovery and control through the platform's existing helper channel. */
public interface LogicFlowDebugRuntime {
  record Candidate(String runtimeId, String profile, String backend, String label, boolean supported) {}
  enum Operation { START, RENEW, STOP, STATUS }

  List<Candidate> candidates(String appName, String owner);

  FlowDebugTarget probe(String appName, String owner, String runtimeId, String captureId);

  FlowDebugCaptureStatus control(Capture capture, Operation operation, long now);
}
