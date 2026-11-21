package com.ouroboros.businessmodel.action;

import java.time.Instant;
import java.util.List;

import com.ouroboros.data.model.DataModelMutation;

/** Lifecycle facts for one Action execution, finalized after its owning transaction completes. */
public record BusinessActionExecutionAuditRecord(String executionId,
                                                 String parentExecutionId,
                                                 String modelFullName,
                                                 String modelRevision,
                                                 String actionName,
                                                 String implementationRef,
                                                 String transactionResult,
                                                 String failureType,
                                                 String actorRef,
                                                 BusinessObjectReference target,
                                                 Instant startedAt,
                                                 Instant completedAt,
                                                 List<DataModelMutation> writeSet,
                                                 List<String> eventIds,
                                                 String executionAuthorityName) {
  public BusinessActionExecutionAuditRecord(String executionId, String parentExecutionId, String modelFullName,
                                           String modelRevision, String actionName, String implementationRef,
                                           String transactionResult, String failureType, String actorRef,
                                           BusinessObjectReference target, Instant startedAt, Instant completedAt,
                                           List<DataModelMutation> writeSet, List<String> eventIds) {
    this(executionId, parentExecutionId, modelFullName, modelRevision, actionName, implementationRef,
        transactionResult, failureType, actorRef, target, startedAt, completedAt, writeSet, eventIds,
        modelFullName + "." + actionName);
  }

  public BusinessActionExecutionAuditRecord {
    writeSet = writeSet == null ? List.of() : List.copyOf(writeSet);
    eventIds = eventIds == null ? List.of() : List.copyOf(eventIds);
  }
}
