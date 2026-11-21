package com.ouroboros.logicflow.dev.service.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.logicflow.exception.EntryNotFoundException;
import com.ouroboros.logicflow.exception.ErrorEntryNotFoundException;
import com.ouroboros.logicflow.exception.ExitNotFoundException;
import com.ouroboros.logicflow.exception.InvalidFlowScopeDetectedException;
import com.ouroboros.logicflow.exception.InvalidLeafNodeDetectedException;
import com.ouroboros.logicflow.exception.InvalidConnectionDetectedException;
import com.ouroboros.logicflow.exception.MissingSuccessfulTerminalException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.exception.ProblematicNodesDetectedException;
import com.ouroboros.logicflow.exception.SuccessfulTerminalConflictDetectedException;
import com.ouroboros.logicflow.exception.UnconnectedInputPortException;
import com.ouroboros.logicflow.exception.UnreachableNodeDetectedException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelValidationDiagnostic;

public final class LogicFlowValidationSnapshotSupport {
  private LogicFlowValidationSnapshotSupport() {
  }

  public static FlowModelMeta normalize(FlowModelMeta model, FlowModel flowModel) {
    var validationDiagnostics = collectValidationDiagnostics(flowModel);
    var validationErrors = validationDiagnostics.stream()
        .map(FlowModelValidationDiagnostic::getMessage)
        .collect(Collectors.toList());
    var validationSummary = validationErrors.isEmpty() ? null : validationErrors.get(0);
    var validatedTime = LocalDateTime.now();

    return model.map(builder -> {
      builder.validationSummary(validationSummary);
      builder.validationErrors(validationErrors);
      builder.validationDiagnostics(validationDiagnostics);
      builder.lastValidatedTime(validatedTime);
    });
  }

  private static List<FlowModelValidationDiagnostic> collectValidationDiagnostics(FlowModel flowModel) {
    if (flowModel == null || flowModel.getErrors().isEmpty()) {
      return Collections.emptyList();
    }
    return flowModel.getErrors().stream()
        .flatMap(error -> mapDiagnostic(error).stream())
        .collect(Collectors.toList());
  }

  private static List<FlowModelValidationDiagnostic> mapDiagnostic(DesignException error) {
    if (error instanceof ProblematicNodesDetectedException problematicNodesDetectedException) {
      return problematicNodesDetectedException.getNodeDesignExceptionList().stream()
          .map(LogicFlowValidationSnapshotSupport::nodeDiagnostic)
          .collect(Collectors.toList());
    }
    if (error instanceof NodeDesignException nodeDesignException) {
      return Collections.singletonList(nodeDiagnostic(nodeDesignException));
    }
    if (error instanceof EntryNotFoundException) {
      return Collections.singletonList(flowDiagnostic("entry_node_not_found", error.getMessage(), "entryNode"));
    }
    if (error instanceof ExitNotFoundException) {
      return Collections.singletonList(flowDiagnostic("exit_node_not_found", error.getMessage(), "exitNode"));
    }
    if (error instanceof ErrorEntryNotFoundException) {
      return Collections.singletonList(flowDiagnostic("error_entry_node_not_found", error.getMessage(), "errorEntryNode"));
    }
    if (error instanceof InvalidConnectionDetectedException invalidConnectionDetectedException) {
      var connections = invalidConnectionDetectedException.getConnections();
      if (!connections.isEmpty()) {
        return connections.stream()
            .map(connection -> FlowModelValidationDiagnostic.builder()
                .diagnosticKind("invalid_connection")
                .message(error.getMessage())
                .nodeNames(Collections.emptyList())
                .scope("edge")
                .fieldPath("targetInputPort")
                .sourceNode(connection.getSourceNode())
                .sourceOutputPort(connection.getSourceOutputPort())
                .targetNode(connection.getTargetNode())
                .targetInputPort(connection.getTargetInputPort())
                .build())
            .collect(Collectors.toList());
      }
    }
    if (error instanceof UnreachableNodeDetectedException unreachableNodeDetectedException) {
      return Collections.singletonList(diagnostic("unreachable_nodes", error.getMessage(),
          unreachableNodeDetectedException.getNodeNames()));
    }
    if (error instanceof InvalidLeafNodeDetectedException invalidLeafNodeDetectedException) {
      return Collections.singletonList(diagnostic("invalid_leaf_nodes", error.getMessage(),
          invalidLeafNodeDetectedException.getNodeNames()));
    }
    if (error instanceof InvalidFlowScopeDetectedException invalidFlowScopeDetectedException) {
      return Collections.singletonList(diagnostic("invalid_flow_scope", error.getMessage(),
          invalidFlowScopeDetectedException.getNodeNames()));
    }
    return Collections.singletonList(diagnostic(globalDiagnosticKind(error), error.getMessage(), Collections.emptyList()));
  }

  private static FlowModelValidationDiagnostic nodeDiagnostic(NodeDesignException error) {
    var nodeNames = new ArrayList<String>();
    if (error.getNode() != null && error.getNode().getName() != null) {
      nodeNames.add(error.getNode().getName());
    }
    return FlowModelValidationDiagnostic.builder()
        .diagnosticKind("node_design_error")
        .message(error.getMessage())
        .nodeNames(nodeNames)
        .scope("node")
        .fieldPath(error.getFieldPath())
        .portKind(error.getPortKind())
        .portIndex(error.getPortIndex())
        .build();
  }

  private static FlowModelValidationDiagnostic flowDiagnostic(
      String diagnosticKind,
      String message,
      String fieldPath
  ) {
    return FlowModelValidationDiagnostic.builder()
        .diagnosticKind(diagnosticKind)
        .message(message)
        .nodeNames(Collections.emptyList())
        .scope("flow")
        .fieldPath(fieldPath)
        .build();
  }

  private static FlowModelValidationDiagnostic diagnostic(
      String diagnosticKind,
      String message,
      List<String> nodeNames
  ) {
    return FlowModelValidationDiagnostic.builder()
        .diagnosticKind(diagnosticKind)
        .message(message)
        .nodeNames(nodeNames)
        .build();
  }

  private static String globalDiagnosticKind(DesignException error) {
    if (error instanceof MissingSuccessfulTerminalException) {
      return "missing_successful_terminal";
    }
    if (error instanceof SuccessfulTerminalConflictDetectedException) {
      return "successful_terminal_conflict";
    }
    if (error instanceof UnconnectedInputPortException) {
      return "unconnected_input_port";
    }
    return toSnakeCase(error.getClass().getSimpleName().replaceFirst("Exception$", ""));
  }

  private static String toSnakeCase(String value) {
    if (value == null || value.isEmpty()) {
      return "unknown_diagnostic";
    }
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .toLowerCase(Locale.ROOT);
  }
}
