package com.ouroboros.workflow.approval.logicflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.DefaultFlowNodeOutPort;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.model.FlowNodePortMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractDecisionNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.logicflow.results.NamedOutPortValueSetter;
import com.ouroboros.logicflow.results.OutPortValueSetter;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;

public class ProcessStatusRouterNode extends AbstractDecisionNode {
  private static final String TYPE = "processStatusRouter";
  private static final String EXCLUSIVE_GROUP_ID = "approval-process-status";
  private static final List<Route> ROUTES = List.of(
      new Route("审批中", "processing"),
      new Route("已暂停", "suspended"),
      new Route("已撤回", "withdrawn"),
      new Route("通过", "approved"),
      new Route("否决", "veto"),
      new Route("撤销", "canceled"),
      new Route("终止", "terminated"),
      new Route("其他", "other")
  );

  private final List<FlowNodePort> outputPorts;
  private final String statusField;
  private final Map<String, String> statusCodeToRoute;

  public ProcessStatusRouterNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    this.outputPorts = buildOutputPorts();
    this.statusField = meta.getExtraProp(String.class, "statusField")
        .map(String::trim)
        .orElse("");
    this.statusCodeToRoute = buildStatusCodeToRoute(meta);
  }

  @Override
  public List<FlowNodePort> getOutputPorts() {
    return outputPorts;
  }

  @Override
  protected Integer maxInputPortCount() {
    return 1;
  }

  @Override
  protected void makeDecision(List<Object> inputs, OutPortValueSetter setPort, NamedOutPortValueSetter setNamedPort) {
    if (inputs.isEmpty()) {
      throw new FlowRuntimeException("流程状态路由节点输入不得为空", this, inputs);
    }
    String route = resolveRoute(inputs.get(0));
    int index = routeIndex(route);
    setPort.accept(index, inputs.get(0));
  }

  private List<FlowNodePort> buildOutputPorts() {
    DataTypeEnum outputType = getInputPorts().isEmpty() ? DataTypeEnum.ANY : getInputPorts().get(0).getDataType();
    List<FlowNodePort> ports = new ArrayList<FlowNodePort>();
    for (int index = 0; index < ROUTES.size(); index++) {
      Route route = ROUTES.get(index);
      FlowNodePortMeta portMeta = FlowNodePortMeta.builder()
          .name(route.label)
          .description(route.label + "分支")
          .dataType(outputType)
          .exclusiveGroupId(EXCLUSIVE_GROUP_ID)
          .defaultBranch("other".equals(route.key))
          .build();
      ports.add(new DefaultFlowNodeOutPort(this, index, portMeta));
    }
    return ports;
  }

  private Map<String, String> buildStatusCodeToRoute(FlowNodeMeta meta) {
    Optional<Object> rawStatusMap = meta.getExtraProp("statusMap");
    if (rawStatusMap.isEmpty() || !(rawStatusMap.get() instanceof Map<?, ?> rawMap)) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new LinkedHashMap<String, String>();
    rawMap.forEach((routeKey, config) -> {
      if (config instanceof Map<?, ?> configMap) {
        normalize(configMap.get("code"))
            .ifPresent(code -> result.put(code, normalizeRouteKey(String.valueOf(routeKey))));
      }
    });
    return result;
  }

  private String resolveRoute(Object input) {
    if (input instanceof FlowProcessInfo processInfo) {
      return resolveFromValues(null, processInfo.getOutcome(), processInfo.getStatus());
    }
    Map<String, Object> payload = resolvePayload(input);
    Object eventType = payload.get("eventType");
    Object outcome = payload.get("outcome");
    Object status = firstPresent(
        payload.get("status"),
        payload.get("state"),
        statusField.isBlank() ? null : payload.get(statusField)
    );
    return resolveFromValues(eventType, outcome, normalizeStatusValue(status).orElse(null));
  }

  private Map<String, Object> resolvePayload(Object input) {
    Map<String, Object> root = toObjectMap(input);
    if (root.get("processInfo") instanceof Map<?, ?> processInfo) {
      return toObjectMap(processInfo);
    }
    if (isWorkflowStatusPayload(root)) {
      return root;
    }
    Object data = root.get("data");
    if (data instanceof Map<?, ?> dataMap && isWorkflowStatusPayload(toObjectMap(dataMap))) {
      return toObjectMap(dataMap);
    }
    Object body = root.get("body");
    if (body instanceof Map<?, ?> bodyMap && isWorkflowStatusPayload(toObjectMap(bodyMap))) {
      return toObjectMap(bodyMap);
    }
    return root;
  }

  private boolean isWorkflowStatusPayload(Map<String, Object> payload) {
    return payload.containsKey("eventType")
        || payload.containsKey("outcome")
        || payload.containsKey("status")
        || payload.containsKey("state")
        || (!statusField.isBlank() && payload.containsKey(statusField));
  }

  private String resolveFromValues(Object eventType, Object outcome, Object status) {
    Optional<String> routeByOutcome = routeByOutcome(outcome);
    if (routeByOutcome.isPresent()) {
      return routeByOutcome.get();
    }
    Optional<String> routeByEvent = routeByEventType(eventType);
    if (routeByEvent.isPresent()) {
      return routeByEvent.get();
    }
    return routeByStatus(status).orElse("other");
  }

  private Optional<String> routeByEventType(Object value) {
    return normalize(value).flatMap(eventType -> switch (eventType) {
      case "started" -> Optional.of("processing");
      case "suspended" -> Optional.of("suspended");
      case "resumed" -> Optional.of("processing");
      case "approved" -> Optional.of("approved");
      case "rejected" -> Optional.of("veto");
      case "cancelled", "canceled" -> Optional.of("canceled");
      case "terminated" -> Optional.of("terminated");
      default -> Optional.empty();
    });
  }

  private Optional<String> routeByOutcome(Object value) {
    return normalize(value).flatMap(outcome -> switch (outcome) {
      case "approved", "pass", "passed" -> Optional.of("approved");
      case "rejected", "reject", "veto" -> Optional.of("veto");
      case "cancelled", "canceled" -> Optional.of("canceled");
      case "withdrawn", "withdraw" -> Optional.of("withdrawn");
      case "suspended" -> Optional.of("suspended");
      case "terminated" -> Optional.of("terminated");
      default -> Optional.empty();
    });
  }

  private Optional<String> routeByStatus(Object value) {
    Optional<String> normalized = normalize(value);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    String status = normalized.get();
    if (statusCodeToRoute.containsKey(status)) {
      return Optional.of(statusCodeToRoute.get(status));
    }
    return switch (status) {
      case "running", "processing", "active", "started" -> Optional.of("processing");
      case "suspended", "paused" -> Optional.of("suspended");
      case "withdrawn", "withdraw" -> Optional.of("withdrawn");
      case "approved" -> Optional.of("approved");
      case "rejected", "reject", "veto" -> Optional.of("veto");
      case "cancelled", "canceled" -> Optional.of("canceled");
      case "terminated" -> Optional.of("terminated");
      default -> Optional.empty();
    };
  }

  private Optional<Object> normalizeStatusValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Object code = map.get("code");
      if (code != null) {
        return Optional.of(code);
      }
    }
    return Optional.ofNullable(value);
  }

  private String normalizeRouteKey(String value) {
    return switch (normalize(value).orElse("")) {
      case "approved" -> "approved";
      case "rejected", "veto" -> "veto";
      case "cancelled", "canceled" -> "canceled";
      case "withdrawn", "withdraw" -> "withdrawn";
      case "suspended", "paused" -> "suspended";
      case "terminated" -> "terminated";
      case "processing", "running", "submitted" -> "processing";
      default -> "other";
    };
  }

  private Optional<String> normalize(Object value) {
    return Optional.ofNullable(value)
        .map(String::valueOf)
        .map(String::trim)
        .filter(text -> !text.isEmpty())
        .map(text -> text.toLowerCase(Locale.ROOT));
  }

  private Object firstPresent(Object... values) {
    for (Object value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private int routeIndex(String routeKey) {
    for (int index = 0; index < ROUTES.size(); index++) {
      if (ROUTES.get(index).key.equals(routeKey)) {
        return index;
      }
    }
    return ROUTES.size() - 1;
  }

  private Map<String, Object> toObjectMap(Object input) {
    if (!(input instanceof Map<?, ?> raw)) {
      return Collections.emptyMap();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    raw.forEach((key, value) -> {
      if (key != null) {
        result.put(String.valueOf(key), value);
      }
    });
    return result;
  }

  private static class Route {
    private final String label;
    private final String key;

    private Route(String label, String key) {
      this.label = label;
      this.key = key;
    }
  }

  public static class Builder implements NodeBuilder<ProcessStatusRouterNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return TYPE.equalsIgnoreCase(meta.getType());
    }

    @Override
    public ProcessStatusRouterNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ProcessStatusRouterNode(owner, metadata);
    }
  }
}
