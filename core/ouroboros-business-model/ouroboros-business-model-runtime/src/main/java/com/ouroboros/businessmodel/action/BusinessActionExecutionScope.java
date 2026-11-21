package com.ouroboros.businessmodel.action;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.ouroboros.data.model.DataModelMutation;
import com.ouroboros.data.model.DataModelMutationObserver;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;

/** Thread-scoped facts for nested synchronous Action calls and emitted Event identities. */
public final class BusinessActionExecutionScope {
  private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

  private BusinessActionExecutionScope() {
  }

  static <T> T with(Execution execution, Supplier<T> operation) {
    Scope previous = CURRENT.get();
    CURRENT.set(new Scope(execution, previous == null || previous.requestInheritanceAllowed()));
    try {
      return operation.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  public static Optional<String> currentExecutionId() {
    return Optional.ofNullable(current()).map(Execution::executionId);
  }

  /** Delivery attempts start a new business execution even when explicitly driven after a source commit. */
  public static <T> T detached(Supplier<T> operation) {
    Scope previous = CURRENT.get();
    CURRENT.set(new Scope(null, false));
    try {
      return operation.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  public static void recordEventId(String eventId) {
    Execution current = current();
    if (current != null && eventId != null && !eventId.isBlank()) {
      current.recordEventId(eventId);
    }
  }

  public static Optional<BusinessActionEventContext> currentEventContext() {
    return Optional.ofNullable(current()).map(Execution::eventContext);
  }

  static Execution current() {
    return CURRENT.get() == null ? null : CURRENT.get().execution();
  }

  public static boolean isRequestInheritanceAllowed() {
    return CURRENT.get() == null || CURRENT.get().requestInheritanceAllowed();
  }

  private record Scope(Execution execution, boolean requestInheritanceAllowed) {
  }

  static final class Execution implements DataModelMutationObserver {
    private final String executionId;
    private final String parentExecutionId;
    private final String modelFullName;
    private final String modelRevision;
    private final String actionName;
    private final String executionAuthorityName;
    private final String implementationRef;
    private final String actorRef;
    private final java.util.Map<String, Object> request;
    private final boolean readOnly;
    private final Set<BusinessEventIdentity> emittedEvents;
    private final Instant startedAt;
    private final List<DataModelMutation> writeSet = new ArrayList<>();
    private final List<DataModelMutation> ownWriteSet = new ArrayList<>();
    private final List<String> eventIds = new ArrayList<>();
    private String failureType;

    Execution(String executionId,
              String parentExecutionId,
              String modelFullName,
              String modelRevision,
              String actionName,
              String implementationRef,
              String actorRef,
              java.util.Map<String, Object> request,
              boolean readOnly,
              Set<BusinessEventIdentity> emittedEvents,
              Instant startedAt) {
      this(executionId, parentExecutionId, modelFullName, modelRevision, actionName, implementationRef,
          actorRef, request, readOnly, emittedEvents, startedAt, modelFullName + "." + actionName);
    }

    Execution(String executionId, String parentExecutionId, String modelFullName, String modelRevision,
              String actionName, String implementationRef, String actorRef, java.util.Map<String, Object> request,
              boolean readOnly, Set<BusinessEventIdentity> emittedEvents, Instant startedAt,
              String executionAuthorityName) {
      this.executionAuthorityName = executionAuthorityName;
      this.executionId = executionId;
      this.parentExecutionId = parentExecutionId;
      this.modelFullName = modelFullName;
      this.modelRevision = modelRevision;
      this.actionName = actionName;
      this.implementationRef = implementationRef;
      this.actorRef = actorRef;
      this.request = request;
      this.readOnly = readOnly;
      this.emittedEvents = Set.copyOf(emittedEvents);
      this.startedAt = startedAt;
    }

    @Override
    public synchronized void onMutation(DataModelMutation mutation) {
      writeSet.add(mutation);
      ownWriteSet.add(mutation);
    }

    synchronized List<DataModelMutation> ownMutations() {
      return List.copyOf(ownWriteSet);
    }

    synchronized void recordEventId(String eventId) {
      eventIds.add(eventId);
    }

    synchronized void recordFailure(Throwable error) {
      if (failureType == null && error != null) {
        failureType = error.getClass().getName();
      }
    }

    /** 把子执行的写集与事件标识归属到父执行，嵌套同步调用共享同一业务事实。 */
    synchronized void mergeInto(Execution parent) {
      if (parent == null) {
        return;
      }
      parent.writeSet.addAll(writeSet);
      parent.eventIds.addAll(eventIds);
    }

    String executionId() {
      return executionId;
    }

    String parentExecutionId() {
      return parentExecutionId;
    }

    java.util.Map<String, Object> request() {
      return request;
    }

    boolean readOnly() {
      return readOnly;
    }

    BusinessActionEventContext eventContext() {
      return new BusinessActionEventContext(modelFullName, actionName, executionId, modelRevision,
          actorRef, readOnly, emittedEvents);
    }

    synchronized String failureType() {
      return failureType;
    }

    synchronized BusinessActionExecutionAuditRecord auditRecord(Instant completedAt, String transactionResult) {
      return new BusinessActionExecutionAuditRecord(
          executionId, parentExecutionId, modelFullName, modelRevision, actionName,
          implementationRef, transactionResult, failureType, actorRef, null,
          startedAt, completedAt, writeSet, eventIds, executionAuthorityName);
    }
  }
}
