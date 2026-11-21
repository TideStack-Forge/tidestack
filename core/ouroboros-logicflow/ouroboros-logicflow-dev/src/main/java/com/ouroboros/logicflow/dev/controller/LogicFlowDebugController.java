package com.ouroboros.logicflow.dev.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.diagnostic.FlowDebugEvent;
import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.dev.service.DebugStoreException;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugCaptureService;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugCaptureService.CaptureView;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugRuntime.Candidate;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.Capture;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore.EventPage;
import com.ouroboros.logicflow.dev.model.LogicFlowDebugEventView;
import com.ouroboros.logicflow.dev.model.LogicFlowDebugSessionView;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugEventStore;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@Tag(name = "LogicFlow online debugging")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid debug operation, cursor or expired control"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Application edit permission required"),
    @ApiResponse(responseCode = "404", description = "Recording unavailable to this owner or expired")
})
@RestController
@RequestMapping("/api/dev/{appName}/logicflow/debug")
public class LogicFlowDebugController {
  private final LogicFlowDebugEventStore eventStore;
  private final LogicFlowDebugCaptureService captures;

  public record StartRequest(
      @Schema(description = "Selected current TEST_APP runtime ID", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 256) String runtimeId,
      @Schema(description = "Root flow to capture", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 256) String flowName,
      @Schema(description = "Retry the same UUID after a lost start response", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED) String operationId) {}

  public LogicFlowDebugController(LogicFlowDebugEventStore eventStore, LogicFlowDebugCaptureService captures) {
    this.eventStore = eventStore;
    this.captures = captures;
  }

  @GetMapping("/targets")
  @Operation(operationId = "listLogicFlowDebugTargets", summary = "列出可调试的测试容器")
  public WebApiResult<List<Candidate>> targets(@PathVariable("appName") String appName) {
    return response(() -> captures.candidates(appName));
  }

  @GetMapping("/captures")
  @Operation(operationId = "listLogicFlowDebugCaptures", summary = "列出当前用户的临时采集记录")
  public WebApiResult<List<CaptureView>> captures(@PathVariable("appName") String appName,
      @RequestParam(name = "flowName", required = false) String flowName) {
    return response(() -> captures.list(appName, flowName));
  }

  @PostMapping("/captures")
  @Operation(operationId = "startLogicFlowDebugCapture", summary = "开始采集选定测试容器中的逻辑流")
  public WebApiResult<CaptureView> start(@PathVariable("appName") String appName, @RequestBody StartRequest request) {
    return response(() -> captures.start(appName, request.runtimeId(), request.flowName(), request.operationId()));
  }

  @GetMapping("/captures/{captureId}")
  @Operation(operationId = "getLogicFlowDebugCapture", summary = "读取采集状态与执行列表")
  public WebApiResult<CaptureView> capture(@PathVariable("appName") String appName, @PathVariable("captureId") String captureId) {
    return response(() -> captures.read(appName, captureId));
  }

  @PostMapping("/captures/{captureId}/renew")
  @Operation(operationId = "renewLogicFlowDebugCapture", summary = "续租采集并确认已接收事件")
  public WebApiResult<CaptureView> renew(@PathVariable("appName") String appName, @PathVariable("captureId") String captureId) {
    return response(() -> captures.renew(appName, captureId));
  }

  @PostMapping("/captures/{captureId}/stop")
  @Operation(operationId = "stopLogicFlowDebugCapture", summary = "停止采集并排空事件")
  public WebApiResult<CaptureView> stop(@PathVariable("appName") String appName, @PathVariable("captureId") String captureId) {
    return response(() -> captures.stop(appName, captureId));
  }

  @GetMapping("/captures/{captureId}/runs/{runId}/events")
  @Operation(operationId = "getLogicFlowDebugEvents", summary = "按执行序号读取事件页")
  public WebApiResult<EventPage> runEvents(@PathVariable("appName") String appName,
      @PathVariable("captureId") String captureId, @PathVariable("runId") String runId,
      @RequestParam(name = "afterSeq", defaultValue = "0") long afterSeq,
      @RequestParam(name = "limit", defaultValue = "100") int limit) {
    return response(() -> {
      captures.require(appName, captureId);
      return eventStore.readEvents(appName, captureId, runId, afterSeq, limit);
    });
  }

  @GetMapping("/captures/{captureId}/graphs/{graphId}")
  @Operation(operationId = "getLogicFlowDebugGraph", summary = "读取执行使用的不可变运行图")
  public WebApiResult<FlowDebugGraph> graph(@PathVariable("appName") String appName,
      @PathVariable("captureId") String captureId, @PathVariable("graphId") String graphId) {
    return response(() -> {
      captures.require(appName, captureId);
      return eventStore.getGraph(appName, captureId, graphId)
          .orElseThrow(() -> new BusinessException(400, "运行时画布不存在或已过期"));
    });
  }

  @GetMapping("/sessions")
  @Operation(operationId = "listLogicFlowDebugSessions", summary = "列出当前用户的执行记录")
  public WebApiResult<Map<String, Object>> sessions(
      @PathVariable("appName") String appName,
      @RequestParam(name = "flowName", required = false) String flowName) {
    return response(() -> Map.of("sessions", captures.recordings(appName).stream()
        .flatMap(capture -> capture.runs().stream())
        .filter(run -> flowName == null || flowName.equals(run.flowName()))
        .map(run -> toSessionView(appName, run)).toList()));
  }

  @GetMapping("/sessions/{sessionId}")
  @Operation(operationId = "getLogicFlowDebugSession", summary = "读取单次执行摘要")
  public WebApiResult<LogicFlowDebugSessionView> sessionDetail(
      @PathVariable("appName") String appName,
      @PathVariable("sessionId") String sessionId) {
    return response(() -> toSessionView(appName, requireSession(appName, sessionId).runs().stream()
        .filter(run -> run.runId().equals(sessionId)).findFirst().orElseThrow()));
  }

  @GetMapping("/sessions/{sessionId}/events")
  @Operation(operationId = "getLogicFlowDebugSessionEvents", summary = "读取单次执行事件列表")
  public ItemsWebApiResult<LogicFlowDebugEventView> events(
      @PathVariable("appName") String appName,
      @PathVariable("sessionId") String sessionId,
      @RequestParam(name = "offset", defaultValue = "0") int offset,
      @RequestParam(name = "limit", defaultValue = "100") int limit) {
    var capture = requireSession(appName, sessionId);
    List<FlowDebugEvent> events;
    try {
      events = eventStore.readEvents(appName, capture.captureId(), sessionId, offset, limit).items();
    } catch (DebugStoreException invalid) {
      throw new BusinessException(400, invalid.getMessage(), invalid);
    }
    List<LogicFlowDebugEventView> views = events.stream()
        .map(this::toEventView)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(views);
  }

  @GetMapping("/sessions/{sessionId}/events/{seq}")
  @Operation(operationId = "getLogicFlowDebugSessionEvent", summary = "读取指定执行事件")
  public WebApiResult<FlowDebugEvent> event(
      @PathVariable("appName") String appName,
      @PathVariable("sessionId") String sessionId,
      @PathVariable("seq") long seq) {
    return response(() -> {
      var capture = requireSession(appName, sessionId);
      if (seq < 1) {
        throw new BusinessException(400, "调试事件序号无效");
      }
      return eventStore.readEvents(appName, capture.captureId(), sessionId, seq - 1, 1).items().stream()
          .findFirst().orElseThrow(() -> new BusinessException(400, "调试事件不存在或已过期"));
    });
  }

  private Capture requireSession(String appName, String sessionId) {
    return captures.recordings(appName).stream()
        .filter(capture -> capture.runs().stream().anyMatch(run -> run.runId().equals(sessionId)))
        .findFirst().orElseThrow(() -> new BusinessException(400, "调试记录不存在或已过期"));
  }

  private LogicFlowDebugSessionView toSessionView(String app, LogicFlowDebugEventStore.Run run) {
    var view = new LogicFlowDebugSessionView();
    view.setAppName(app);
    view.setSessionId(run.runId());
    view.setFlowName(run.flowName());
    view.setStatus(run.incomplete() ? "INCOMPLETE" : run.status());
    view.setStartedAt(run.startedAt());
    view.setLastEventAt(run.lastEventAt());
    view.setEventCount(Math.toIntExact(run.highestSeq()));
    return view;
  }

  private <T> WebApiResult<T> response(Supplier<T> request) {
    try {
      return WebApiResult.ok(request.get());
    } catch (DebugStoreException rejected) {
      throw new BusinessException(400, rejected.getMessage(), rejected);
    }
  }

  private LogicFlowDebugEventView toEventView(FlowDebugEvent event) {
    LogicFlowDebugEventView view = new LogicFlowDebugEventView();
    view.setSeq(event.getSeq() != null ? event.getSeq().intValue() : null);
    view.setTimestamp(event.getTimestamp());
    view.setEventType(event.getKind());
    view.setNodeName(event.getNodeName());
    view.setMessage(buildMessage(event));
    view.setPayload(event.getPayload());
    return view;
  }

  private String buildMessage(FlowDebugEvent event) {
    String kind = event.getKind();
    if (kind == null) {
      return "";
    }
    switch (kind) {
      case "flow.started":
        return "逻辑流开始执行";
      case "flow.completed":
        return "逻辑流执行完成";
      case "flow.failed":
        return "逻辑流执行失败";
      case "node.started":
        return "节点 " + event.getNodeName() + " 开始执行";
      case "node.completed":
        return "节点 " + event.getNodeName() + " 执行完成";
      case "node.failed":
        return "节点 " + event.getNodeName() + " 执行失败";
      case "node.skipped":
        return "节点 " + event.getNodeName() + " 被跳过";
      default:
        return kind;
    }
  }
}
