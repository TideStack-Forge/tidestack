package com.ouroboros.logicflow.dev.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugCaptureStatus.Loss;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.diagnostic.FlowDebugTarget;

/** Shared, disposable debug recordings. Successful receipt is not a durability guarantee. */
public interface LogicFlowDebugEventStore {
  enum State { STARTING, ACTIVE, SATURATED, DRAINING, CLOSED }
  record Capabilities(boolean shared, boolean atomicControl, boolean durableHistory) {}
  record CreateCapture(String captureId, String operationId, String owner, FlowDebugTarget target,
                       String flowName, long leaseMillis) {}
  record EventReservation(long captureSeq, String runId, long runSeq, String digest) {}
  record GraphRecord(String graphId, String digest, int bytes) {}
  record Capture(String captureId, String operationId, String owner, FlowDebugTarget target,
                 String flowName, State state, long version, long createdAt, long leaseUntil,
                 long expiresAt, long receivedThrough, long bytes, List<Run> runs, List<Long> pending, List<Loss> losses, List<GraphRecord> graphs, List<EventReservation> staged) {
    public Capture {
      runs = List.copyOf(runs);
      pending = List.copyOf(pending);
      losses = List.copyOf(losses);
      graphs = List.copyOf(graphs);
      staged = List.copyOf(staged);
    }
  }
  record Run(String runId, String flowName, String graphId, long firstCaptureSeq, long startedAt,
             long lastEventAt, long highestSeq, Long terminalSeq, String status, boolean incomplete) {}
  record Receipt(long receivedThrough, boolean duplicate) {}
  record EventPage(List<FlowDebugEvent> items, long afterSeq, long highestSeq, boolean hasMore,
                   boolean complete, String availability) {
    public EventPage { items = List.copyOf(items); }
  }

  Capabilities capabilities();
  Capture tryCreateCapture(CreateCapture command);
  Optional<Capture> getCapture(String appName, String captureId);
  Capture updateCapture(String appName, String captureId, long expectedVersion, State state, long leaseMillis);
  List<Capture> listCaptures(String appName);
  void putGraph(String appName, String captureId, FlowDebugGraph graph);
  Optional<FlowDebugGraph> getGraph(String appName, String captureId, String graphId);
  Receipt append(FlowDebugEvent event);
  Receipt declareLoss(String appName, String captureId, FlowDebugTarget target, Loss loss);
  EventPage readEvents(String appName, String captureId, String runId, long afterSeq, int limit);

}
