package com.ouroboros.logicflow.compiler;

import static com.ouroboros.logicflow.util.LogicFlowUtil.topologicalSort;
import static io.vavr.API.$;
import static io.vavr.API.Case;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.diagnostic.FlowInputTransfer;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.nodes.AbstractTerminalNode;
import com.ouroboros.logicflow.results.Decision;
import com.ouroboros.logicflow.results.FlowOutcome;
import com.ouroboros.logicflow.support.LogicFlowServices;
import com.ouroboros.logicflow.util.LogicFlowUtil;

public class FunctionalFlowCompiler {
  public static final FunctionalFlowCompiler INSTANCE = new FunctionalFlowCompiler();
  private static final Logger LOGGER = LoggerFactory.getLogger(FunctionalFlowCompiler.class);
  private static final AtomicLong NEXT_DIAGNOSTIC_WARNING = new AtomicLong();
  private static final ThreadLocal<NodeInvocation> CURRENT_NODE = new ThreadLocal<>();

  public Function<Object[], Try<Object>> compile(FlowModel flowModel) {
    return doCompile(flowModel);
  }

  private Function<Object[], Try<Object>> doCompile(FlowModel model) {
    final var mainScope = compileScope(model, LogicFlowUtil.getMainFlowNodes(model), model.getExitNode());
    final var errorScopeNodes = LogicFlowUtil.getErrorFlowNodes(model);
    final var errorScope = errorScopeNodes.isEmpty()
        ? null
        : compileScope(model, errorScopeNodes, null);

    return input -> executeCompiledFlow(model, mainScope, errorScope, input);
  }

  private Try<Object> executeCompiledFlow(FlowModel model,
                                          CompiledScope mainScope,
                                          CompiledScope errorScope,
                                          Object[] input) {
    var flowInputs = Arrays.asList(input);
    var analyzerContext = createAnalyzerContext(model, flowInputs);

    var mainExecution = mainScope.execute(input, analyzerContext);
    var nodeResults = new ArrayList<Either<Throwable, Object>>(mainExecution.getNodeResults());
    var finalOutcome = mainExecution.getOutcome();

    if (mainExecution.started && finalOutcome.isFailure() && errorScope != null) {
      var errorContext = buildErrorContext(finalOutcome.getCause(), model, flowInputs);
      var errorExecution = errorScope.execute(new Object[]{errorContext}, analyzerContext == null ? null : analyzerContext.forScope("error"));
      nodeResults.addAll(errorExecution.getNodeResults());
      finalOutcome = errorExecution.getOutcome();
    }

    notifyFlowCompletion(analyzerContext, nodeResults, finalOutcome);
    return finalOutcome.map(FunctionalFlowCompiler::unwrapOutcomeValue);
  }

  private CompiledScope compileScope(FlowModel model,
                                     Set<FlowNode> scopeNodes,
                                     FlowNode explicitExitNode) {
    final var sortedNodeList = topologicalSort(model).stream()
        .filter(scopeNodes::contains)
        .collect(Collectors.toList());
    final var allNodesInPortsMapping = buildNodeInputPortMapping(sortedNodeList, scopeNodes);
    final var firstLayerMaxInputPortCount = sortedNodeList.stream()
        .filter(node -> getScopedIncomingConnections(node, scopeNodes).isEmpty())
        .mapToInt(node -> node.getInputPorts().size())
        .max()
        .orElse(0);

    final List<CompiledFlowNode> compiledFlowNodes = new ArrayList<>();
    for (int i = 0; i < sortedNodeList.size(); i++) {
      compiledFlowNodes.add(compileNode(sortedNodeList.get(i), allNodesInPortsMapping.get(i)));
    }

    final var compiledFlow = compiledFlowNodes.stream()
        .reduce(CompiledFlowNode::connect)
        .orElse((nodeResultFutureList, analyzerContext) -> nodeResultFutureList);

    final var terminalNodeIndexSet = new LinkedHashSet<Integer>();
    final var successfulOutcomeKindMap = new HashMap<Integer, FlowOutcome.Kind>();
    final var exitNodeIndex = sortedNodeList.indexOf(explicitExitNode);
    if (exitNodeIndex >= 0) {
      terminalNodeIndexSet.add(exitNodeIndex);
      successfulOutcomeKindMap.put(exitNodeIndex, FlowOutcome.Kind.EXIT);
    }

    sortedNodeList.stream()
        .filter(AbstractTerminalNode.class::isInstance)
        .mapToInt(sortedNodeList::indexOf)
        .forEach(index -> {
          terminalNodeIndexSet.add(index);
          successfulOutcomeKindMap.put(index,
              ((AbstractTerminalNode) sortedNodeList.get(index)).getSuccessfulOutcomeKind());
        });

    return new CompiledScope(sortedNodeList,
        firstLayerMaxInputPortCount,
        compiledFlow,
        terminalNodeIndexSet,
        successfulOutcomeKindMap);
  }

  private CompiledFlowNode compileNode(FlowNode node, List<InputPortMapping> inPortsMapping) {
    final var argumentsMapper = buildArgumentsMapper(node, inPortsMapping);

    final var isAsync = node.isAsync();

    return (nodeResultFutureList, analyzerContext) -> {
      final Function<List<Object>, List<Object>> nodeExecutor = params -> executeNode(node, params, analyzerContext);
      final var argumentsFuture = argumentsMapper.apply(nodeResultFutureList, analyzerContext);
      final var nodeArgumentsFuture = CompletableFuture.allOf(argumentsFuture.toArray(CompletableFuture<?>[]::new))
          .thenApply(v -> argumentsFuture.stream().map(CompletableFuture::join).collect(Collectors.toList()))
          .handle((params, failure) -> {
            if (failure != null) {
              if (analyzerContext != null) {
                analyzerContext.analyzers.forEach(a -> a.onNodeBlocked(
                    analyzerContext.context, analyzerContext.model, node, unwrapCompletionException(failure)));
              }
              throw new CompletionException(unwrapCompletionException(failure));
            }
            return params;
          });
      final var nodeResultFuture = isAsync
          ? nodeArgumentsFuture.thenApplyAsync(nodeExecutor)
          : nodeArgumentsFuture.thenApply(nodeExecutor);
      nodeResultFutureList.add(nodeResultFuture);
      return nodeResultFutureList;
    };
  }

  private List<Object> executeNode(FlowNode node, List<Object> params, AnalyzerContext analyzerContext) {
    var previous = CURRENT_NODE.get();
    if (analyzerContext != null) {
      CURRENT_NODE.set(new NodeInvocation(analyzerContext.context, node.getName()));
    } else {
      // An unobserved invocation interrupts ancestry. A deeper observed flow starts a new trace.
      CURRENT_NODE.remove();
    }
    try {
      return executeNodeWithDiagnostics(node, params, analyzerContext);
    } finally {
      if (previous == null) {
        CURRENT_NODE.remove();
      } else {
        CURRENT_NODE.set(previous);
      }
    }
  }

  private List<Object> executeNodeWithDiagnostics(FlowNode node, List<Object> params, AnalyzerContext analyzerContext) {
    if (params.stream().anyMatch(p -> p instanceof Decision decision && !decision.isSatisfied())) {
      if (analyzerContext != null) {
        analyzerContext.analyzers.forEach(a -> a.onNodeSkipped(analyzerContext.context, analyzerContext.model, node, params));
      }
      return node.getOutputPorts().stream().map(p -> Decision.unsatisfied()).collect(Collectors.toList());
    }

    final var finalParams = params.stream()
        .map(p -> p instanceof Decision decision ? decision.getValue() : p)
        .collect(Collectors.toList());

    if (analyzerContext != null) {
      analyzerContext.analyzers.forEach(a -> a.onNodeStart(analyzerContext.context, analyzerContext.model, node, finalParams));
    }
    try {
      final var result = node.execute(finalParams).getOrElseThrow(e -> {
        if (e instanceof FlowRuntimeException flowRuntimeException) {
          throw flowRuntimeException;
        }
        throw new FlowRuntimeException("执行节点失败", e, node, finalParams);
      });
      final var validatedResult = validateSelectiveOutputContract(node, finalParams, result);
      if (analyzerContext != null) {
        analyzerContext.analyzers.forEach(a -> a.onNodeCompleted(
            analyzerContext.context, analyzerContext.model, node, finalParams, validatedResult));
      }
      return validatedResult;
    } catch (RuntimeException failure) {
      if (analyzerContext != null) {
        analyzerContext.analyzers.forEach(a -> a.onNodeFailed(
            analyzerContext.context, analyzerContext.model, node, finalParams, failure));
      }
      throw failure;
    }
  }

  private List<List<InputPortMapping>> buildNodeInputPortMapping(List<FlowNode> sortedNodeList,
                                                                 Set<FlowNode> scopeNodes) {
    return sortedNodeList.stream()
        .map(node -> {
          final var incomingConnections = getScopedIncomingConnections(node, scopeNodes);
          if (incomingConnections.isEmpty()) {
            List<InputPortMapping> inputPortMapping = new ArrayList<>();
            for (int i = 0; i < node.getInputPorts().size(); i++) {
              inputPortMapping.add(new InputPortMapping(-1, i, i, null));
            }
            return inputPortMapping;
          }
          return incomingConnections.stream()
              .sorted(Comparator.comparingInt(FlowConnection::getTargetInputPort))
              .map(connection -> {
                final var sourceNode = connection.getSourceNode();
                final var sourceNodeIndex = sortedNodeList.indexOf(sourceNode);
                final var sourceNodeOutPortIndex = connection.getSourceOutputPort();
                final var inPortIndex = connection.getTargetInputPort();
                return new InputPortMapping(sourceNodeIndex, sourceNodeOutPortIndex, inPortIndex, sourceNode.getName());
              })
              .collect(Collectors.toList());
        })
        .collect(Collectors.toList());
  }

  private FlowNodeParamsMapper buildArgumentsMapper(FlowNode node, List<InputPortMapping> inPortsMapping) {
    final var finalInPortsMapping = new ArrayList<List<InputPortMapping>>(node.getInputPorts().size());
    for (int i = 0; i < node.getInputPorts().size(); i++) {
      final var index = i;
      final var portMapping = inPortsMapping.stream()
          .filter(m -> m.targetPortIndex == index)
          .collect(Collectors.toList());
      finalInPortsMapping.add(portMapping);
    }

    return (results, context) -> finalInPortsMapping.stream()
        .map(mappings -> mapInputPort(node, mappings, results, context))
        .toList();
  }

  private CompletableFuture<Object> mapInputPort(FlowNode node, List<InputPortMapping> mappings,
                                                 List<CompletableFuture<List<Object>>> results, AnalyzerContext context) {
    var sources = mappings.stream()
        .map(mapping -> results.get(mapping.getSourceResultIndex()).thenApply(values -> values.get(mapping.sourcePortIndex)))
        .toList();
    return CompletableFuture.allOf(sources.toArray(CompletableFuture<?>[]::new)).thenApply(ignored -> {
      int selected = -1;
      for (int index = 0; index < sources.size(); index++) {
        if (selected < 0 && isSatisfiedSelectiveOutput(sources.get(index).join())) {
          selected = index;
        }
      }
      if (context != null) {
        for (int index = 0; index < sources.size(); index++) {
          var mapping = mappings.get(index);
          var transfer = new FlowInputTransfer(mapping.sourceNodeName, mapping.sourcePortIndex,
              node.getName(), mapping.targetPortIndex, isSatisfiedSelectiveOutput(sources.get(index).join()), index == selected);
          context.analyzers.forEach(a -> a.onInputTransfer(context.context, context.model, transfer));
        }
      }
      if (sources.size() == 1) {
        return sources.getFirst().join();
      }
      if (selected < 0) {
        return Decision.unsatisfied();
      }
      var value = sources.get(selected).join();
      return value instanceof Decision decision ? decision.getValue() : value;
    });
  }

  private List<FlowConnection> getScopedIncomingConnections(FlowNode node, Set<FlowNode> scopeNodes) {
    return node.getIncomingConnections().stream()
        .filter(connection -> scopeNodes.contains(connection.getSourceNode()))
        .collect(Collectors.toList());
  }

  private AnalyzerContext createAnalyzerContext(FlowModel model, List<Object> flowInputs) {
    var analyzers = LogicFlowServices.cachedSortedServiceStream(FlowRuntimeAnalyzer.class)
        .filter(analyzer -> Try.of(() -> analyzer.isEnabled(model))
            .onFailure(failure -> reportDiagnosticFailure(analyzer, failure)).getOrElse(false))
        .collect(() -> new ArrayList<FlowRuntimeAnalyzer>() {
          @Override
          public void forEach(Consumer<? super FlowRuntimeAnalyzer> action) {
            super.forEach(a -> Try.run(() -> action.accept(a)).onFailure(failure -> reportDiagnosticFailure(a, failure)));
          }
        }, List::add, List::addAll);
    if (analyzers.isEmpty()) {
      return null;
    }
    var parent = CURRENT_NODE.get();
    var context = new FlowExecutionContext(UUID.randomUUID().toString(),
        parent == null ? null : parent.context.invocationId(), parent == null ? null : parent.nodeName, "main");
    return new AnalyzerContext(context, model, flowInputs, analyzers);
  }

  private void reportDiagnosticFailure(FlowRuntimeAnalyzer analyzer, Throwable failure) {
    long now = System.nanoTime();
    long next = NEXT_DIAGNOSTIC_WARNING.get();
    if ((next == 0 || now - next >= 0) && NEXT_DIAGNOSTIC_WARNING.compareAndSet(next, now + 60_000_000_000L)) {
      LOGGER.warn("Flow analyzer {} failed; business execution continues (warnings limited to one per minute)",
          analyzer.getClass().getName(), failure);
    }
  }

  private void notifyFlowCompletion(AnalyzerContext analyzerContext,
                                    List<Either<Throwable, Object>> nodeResults,
                                    Try<FlowOutcome> finalOutcome) {
    if (analyzerContext == null || !analyzerContext.started) {
      return;
    }
    if (finalOutcome.isFailure()) {
      analyzerContext.analyzers.forEach(a -> a.onFlowFailed(
          analyzerContext.context,
          analyzerContext.model,
          analyzerContext.flowInputs,
          nodeResults,
          finalOutcome.getCause()));
      return;
    }
    analyzerContext.analyzers.forEach(a -> a.onFlowCompleted(
        analyzerContext.context,
        analyzerContext.model,
        analyzerContext.flowInputs,
        nodeResults,
        unwrapOutcomeValue(finalOutcome.get())));
  }

  private CompletableFuture<FlowOutcome> getResult(List<CompletableFuture<List<Object>>> nodeResultFutureList,
                                                   List<FlowNode> sortedNodeList,
                                                   Set<Integer> terminalNodeIndexSet,
                                                   Map<Integer, FlowOutcome.Kind> successfulOutcomeKindMap) {
    final var allNodeResults = nodeResultFutureList.stream().skip(1).toList();
    if (allNodeResults.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    final var completionSignal = new CompletableFuture<Object>();
    allNodeResults.forEach(nodeResultFuture -> nodeResultFuture.whenComplete((value, throwable) -> {
          if (throwable != null) {
            completionSignal.complete(unwrapCompletionException(throwable));
          }
        }));

    CompletableFuture.allOf(allNodeResults.toArray(CompletableFuture<?>[]::new))
        .thenApply((v) -> terminalNodeIndexSet.stream()
            .map(index -> createSuccessfulOutcome(index, sortedNodeList, nodeResultFutureList.get(index + 1).join(), successfulOutcomeKindMap))
            .filter(Objects::nonNull)
            .collect(Collectors.toList()))
        .thenApply(FunctionalFlowCompiler::selectSingleOutcome)
        .handle((result, throwable) -> {
          if (throwable != null) {
            completionSignal.complete(unwrapCompletionException(throwable));
          } else {
            completionSignal.complete(result);
          }
          return null;
        });

    return completionSignal.thenCompose(signal -> {
      if (signal instanceof Throwable) {
        return failedFuture((Throwable) signal);
      }
      return CompletableFuture.completedFuture((FlowOutcome) signal);
    });
  }

  static FlowOutcome selectSingleOutcome(List<FlowOutcome> outcomes) {
    if (outcomes.isEmpty()) {
      return null;
    }
    if (outcomes.size() > 1) {
      throw new IllegalStateException("检测到多个成功终点，静态分析漏检");
    }
    return outcomes.get(0);
  }

  private static Object unwrapOutcomeValue(FlowOutcome outcome) {
    return outcome == null ? null : outcome.getValue();
  }

  private FlowOutcome createSuccessfulOutcome(int nodeIndex,
                                              List<FlowNode> sortedNodeList,
                                              List<Object> rawResult,
                                              Map<Integer, FlowOutcome.Kind> successfulOutcomeKindMap) {
    if (rawResult.stream().allMatch(o -> o instanceof Decision decision && !decision.isSatisfied())) {
      return null;
    }
    var kind = successfulOutcomeKindMap.get(nodeIndex);
    if (kind == null) {
      return null;
    }
    var nodeName = sortedNodeList.get(nodeIndex).getName();
    var value = rawResult.isEmpty() ? null : rawResult.get(0);
    if (kind == FlowOutcome.Kind.EXIT) {
      return FlowOutcome.exit(nodeName, value);
    }
    return FlowOutcome.returned(nodeName, value);
  }

  private Map<String, Object> buildErrorContext(Throwable failure, FlowModel model, List<Object> flowInputs) {
    var errorContext = new LinkedHashMap<String, Object>();
    var flowRuntimeFailure = findFlowRuntimeFailure(failure);
    var normalizedFailure = unwrapFailure(failure);
    var exceptionType = normalizedFailure.getClass().getSimpleName();
    if (exceptionType == null || exceptionType.trim().isEmpty()) {
      exceptionType = normalizedFailure.getClass().getName();
    }

    errorContext.put("exceptionType", exceptionType);
    errorContext.put("exceptionMessage", normalizedFailure.getMessage());
    errorContext.put("exceptionCode", extractErrorCode(normalizedFailure));
    errorContext.put("flowName", model.getName());
    errorContext.put("flowInputs", new ArrayList<>(flowInputs));
    errorContext.put("nodeName", flowRuntimeFailure == null || flowRuntimeFailure.getNode() == null
        ? null
        : flowRuntimeFailure.getNode().getName());
    errorContext.put("nodeInputs", flowRuntimeFailure == null || flowRuntimeFailure.getInputs() == null
        ? null
        : new ArrayList<>(flowRuntimeFailure.getInputs()));
    return errorContext;
  }

  private Throwable unwrapFailure(Throwable failure) {
    var current = unwrapCompletionException(failure);
    if (current instanceof FlowRuntimeException flowRuntimeException && flowRuntimeException.getCause() != null) {
      return unwrapCompletionException(flowRuntimeException.getCause());
    }
    return current;
  }

  private FlowRuntimeException findFlowRuntimeFailure(Throwable failure) {
    var current = unwrapCompletionException(failure);
    while (current != null) {
      if (current instanceof FlowRuntimeException) {
        return (FlowRuntimeException) current;
      }
      current = current.getCause();
    }
    return null;
  }

  private Integer extractErrorCode(Throwable failure) {
    var current = failure;
    while (current != null) {
      try {
        Method method = current.getClass().getMethod("getErrorCode");
        var value = method.invoke(current);
        if (value instanceof Integer) {
          return (Integer) value;
        }
      }
      catch (ReflectiveOperationException exception) {
        current = current.getCause();
        continue;
      }
      current = current.getCause();
    }
    return null;
  }

  private Throwable unwrapCompletionException(Throwable throwable) {
    var current = throwable;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private <T> CompletableFuture<T> failedFuture(Throwable throwable) {
    var future = new CompletableFuture<T>();
    future.completeExceptionally(throwable);
    return future;
  }

  private List<Object> validateSelectiveOutputContract(FlowNode node,
                                                       List<Object> params,
                                                       List<Object> outputs) {
    if (!Boolean.TRUE.equals(node.supportsSelectiveOutputMetadata()) || outputs == null || outputs.isEmpty()) {
      return outputs;
    }

    node.getOutputPorts().stream()
        .filter(port -> port.getExclusiveGroupId() != null && !port.getExclusiveGroupId().trim().isEmpty())
        .collect(Collectors.groupingBy(FlowNodePort::getExclusiveGroupId))
        .forEach((groupId, ports) -> {
          var satisfiedCount = ports.stream()
              .filter(port -> port.getIndex() >= 0 && port.getIndex() < outputs.size())
              .map(port -> outputs.get(port.getIndex()))
              .filter(this::isSatisfiedSelectiveOutput)
              .count();
          if (satisfiedCount > 1) {
            throw new FlowRuntimeException("同一 exclusiveGroupId 不能同时命中多个输出: " + groupId, node, params);
          }
        });
    return outputs;
  }

  private boolean isSatisfiedSelectiveOutput(Object output) {
    if (output instanceof Decision decision) {
      return decision.isSatisfied();
    }
    return true;
  }

  interface CompiledFlowNode {
    List<CompletableFuture<List<Object>>> apply(List<CompletableFuture<List<Object>>> results, AnalyzerContext context);

    default CompiledFlowNode connect(CompiledFlowNode after) {
      return (nodeResultFutureList, context) -> after.apply(this.apply(nodeResultFutureList, context), context);
    }
  }

  interface FlowNodeParamsMapper {
    List<CompletableFuture<Object>> apply(List<CompletableFuture<List<Object>>> results, AnalyzerContext context);
  }

  @SuppressWarnings("ClassCanBeRecord")
  static class InputPortMapping {
    final int sourceNodeIndex;
    final int sourcePortIndex;
    final int targetPortIndex;
    final String sourceNodeName;

    InputPortMapping(int sourceNodeIndex, int sourcePortIndex, int targetPortIndex, String sourceNodeName) {
      this.sourceNodeIndex = sourceNodeIndex;
      this.sourcePortIndex = sourcePortIndex;
      this.targetPortIndex = targetPortIndex;
      this.sourceNodeName = sourceNodeName;
    }

    int getSourceResultIndex() {
      return sourceNodeIndex + 1;
    }
  }

  final class CompiledScope {
    final List<FlowNode> sortedNodeList;
    final int firstLayerMaxInputPortCount;
    final CompiledFlowNode compiledFlow;
    final Set<Integer> terminalNodeIndexSet;
    final Map<Integer, FlowOutcome.Kind> successfulOutcomeKindMap;

    CompiledScope(List<FlowNode> sortedNodeList,
                  int firstLayerMaxInputPortCount,
                  CompiledFlowNode compiledFlow,
                  Set<Integer> terminalNodeIndexSet,
                  Map<Integer, FlowOutcome.Kind> successfulOutcomeKindMap) {
      this.sortedNodeList = sortedNodeList;
      this.firstLayerMaxInputPortCount = firstLayerMaxInputPortCount;
      this.compiledFlow = compiledFlow;
      this.terminalNodeIndexSet = terminalNodeIndexSet;
      this.successfulOutcomeKindMap = successfulOutcomeKindMap;
    }

    ScopeExecution execute(Object[] input, AnalyzerContext analyzerContext) {
      if (input.length < firstLayerMaxInputPortCount) {
        var firstNode = sortedNodeList.stream()
            .filter(node -> node.getInputPorts().size() > input.length)
            .findFirst()
            .orElse(null);
        return new ScopeExecution(Try.failure(new FlowRuntimeException("输入参数数量不正确", firstNode, Arrays.asList(input))),
            Collections.emptyList(),
            false);
      }

      if (analyzerContext != null) {
        analyzerContext.startIfNeeded();
      }

      final List<CompletableFuture<List<Object>>> nodeResultFutureList = new ArrayList<>(sortedNodeList.size() + 1);
      nodeResultFutureList.add(CompletableFuture.completedFuture(Arrays.asList(input)));
      final var resultFuture = Try.of(() -> compiledFlow.apply(nodeResultFutureList, analyzerContext))
          .map(r -> getResult(r, sortedNodeList, terminalNodeIndexSet, successfulOutcomeKindMap).join())
          .flatMap(outcome -> outcome == null
              ? Try.failure(new IllegalStateException("流程未到达成功终点"))
              : Try.success(outcome))
          .mapFailure(Case($(CompletionException.class::isInstance), FunctionalFlowCompiler.this::unwrapCompletionException));

      return new ScopeExecution(resultFuture, nodeResultFutureList, true);
    }
  }

  final class ScopeExecution {
    final Try<FlowOutcome> outcome;
    final List<CompletableFuture<List<Object>>> nodeResultFutureList;
    final boolean started;

    ScopeExecution(Try<FlowOutcome> outcome,
                   List<CompletableFuture<List<Object>>> nodeResultFutureList,
                   boolean started) {
      this.outcome = outcome;
      this.nodeResultFutureList = nodeResultFutureList;
      this.started = started;
    }

    Try<FlowOutcome> getOutcome() {
      return outcome;
    }

    List<Either<Throwable, Object>> getNodeResults() {
      if (nodeResultFutureList == null || nodeResultFutureList.isEmpty()) {
        return Collections.emptyList();
      }
      return nodeResultFutureList.stream()
          .skip(1)
          .map(f -> f.handle((v, e) -> e == null
              ? Either.<Throwable, Object>right(v)
              : Either.<Throwable, Object>left(unwrapCompletionException(e))))
          .map(CompletableFuture::join)
          .collect(Collectors.toList());
    }
  }

  static final class AnalyzerContext {
    final FlowExecutionContext context;
    final FlowModel model;
    final List<Object> flowInputs;
    final List<FlowRuntimeAnalyzer> analyzers;
    boolean started;

    AnalyzerContext(FlowExecutionContext context,
                    FlowModel model,
                    List<Object> flowInputs,
                    List<FlowRuntimeAnalyzer> analyzers) {
      this.context = context;
      this.model = model;
      this.flowInputs = flowInputs;
      this.analyzers = analyzers;
    }

    AnalyzerContext forScope(String scope) {
      var scoped = new AnalyzerContext(context.withScope(scope), model, flowInputs, analyzers);
      scoped.started = started;
      return scoped;
    }

    void startIfNeeded() {
      if (started) {
        return;
      }
      started = true;
      analyzers.forEach(a -> a.onFlowStart(context, model, flowInputs));
    }
  }

  private record NodeInvocation(FlowExecutionContext context, String nodeName) {
  }
}
