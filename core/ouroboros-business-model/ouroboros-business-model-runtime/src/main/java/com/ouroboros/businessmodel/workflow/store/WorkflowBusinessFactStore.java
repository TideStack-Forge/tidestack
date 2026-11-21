package com.ouroboros.businessmodel.workflow.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/** Receiver persistence inside the callback/Event transaction, using the official DataModel path. */
@Transactional(propagation = Propagation.MANDATORY)
public class WorkflowBusinessFactStore {
  public WorkflowBusinessFactCursor acquire(String modelFullName, Object recordId, String flowKey) {
    requireTransaction();
    var cursor = new WorkflowBusinessFactCursor();
    cursor.setProjectionKey(key(List.of(modelFullName, recordId.toString(), flowKey)));
    cursor.setModelFullName(modelFullName);
    cursor.setRecordId(recordId.toString());
    cursor.setFlowKey(flowKey);
    cursor.setStateSequence(0L);
    Optional<WorkflowBusinessFactCursor> existing = lock(cursor.getProjectionKey());
    if (existing.isPresent()) { return existing.get(); }
    var inserted = cursors().insert(cursor);
    if (inserted.isSuccess()) { return cursor; }
    Throwable failure = inserted.getCause();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof UniqueConstraintViolationException) {
        return lock(cursor.getProjectionKey()).orElseThrow(() -> new BusinessException("业务工作流游标并发创建后不可见", failure));
      }
    }
    throw new BusinessException("创建业务工作流投影游标失败", failure);
  }

  public Optional<WorkflowBusinessFactReceipt> findReceipt(WorkflowBusinessFactCursor cursor, String sourceFactId) {
    requireTransaction();
    var model = receipts().getDataModel();
    var query = QueryStatement.builder().from(model.getFullName(), model.getRawName())
        .select(model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(SExpression.create(Operators.EQ, SExpression.field("receiptKey"),
            SExpression.constant(receiptKey(cursor, sourceFactId)))).build();
    // A source-proof read may have established a MySQL repeatable-read snapshot before the
    // competing receiver committed. Read the receipt under the lock, not from that old snapshot.
    var rows = model.queryForUpdate(query).getOrElseThrow(error -> new BusinessException("锁定工作流事实接收记录失败", error));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().toBean(WorkflowBusinessFactReceipt.class));
  }

  /** Notification reads committed handoff proof without acquiring the receiver's cursor or receipt locks. */
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public Optional<WorkflowBusinessFactReceipt> findReceipt(String modelFullName, Object recordId, String flowKey, String sourceFactId) {
    String projectionKey = key(List.of(modelFullName, recordId.toString(), flowKey));
    var found = receipts().query(List.of(key(List.of(projectionKey, sourceFactId))))
        .getOrElseThrow(error -> new BusinessException("读取工作流通知接收凭据失败", error));
    return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
  }

  public WorkflowBusinessFactReceipt record(WorkflowBusinessFactCursor cursor, String sourceFactId, String instanceId,
      Long stateSequence, String businessEventId, String disposition) {
    requireTransaction();
    var receipt = new WorkflowBusinessFactReceipt();
    receipt.setReceiptKey(receiptKey(cursor, sourceFactId));
    receipt.setProjectionKey(cursor.getProjectionKey());
    receipt.setSourceFactId(sourceFactId);
    receipt.setInstanceId(instanceId);
    receipt.setStateSequence(stateSequence);
    receipt.setBusinessEventId(businessEventId);
    receipt.setDisposition(disposition);
    receipt.setReceivedAt(LocalDateTime.now());
    return receipts().insert(receipt).getOrElseThrow(error -> new BusinessException("工作流事实接收冲突或持久化失败", error));
  }

  /** Caller has verified formal current-round ownership before applying the original callback. */
  public void advance(WorkflowBusinessFactCursor cursor, FlowInstanceEvent event) {
    requireTransaction();
    if (event.getStateSequence() == null || event.getStateSequence() < 1
        || Objects.equals(cursor.getInstanceId(), event.getInstanceId())
            && event.getStateSequence() <= cursor.getStateSequence()) {
      throw new BusinessException("工作流投影游标不能回退或重复推进");
    }
    var changed = new LinkedHashMap<String, Object>();
    changed.put("instanceId", event.getInstanceId());
    changed.put("approvalCaseId", event.getApprovalCaseId());
    changed.put("submissionRound", event.getSubmissionRound());
    changed.put("stateSequence", event.getStateSequence());
    changed.put("lastEventFactId", event.getEventFactId());
    Long rows = cursors().update(cursor.getProjectionKey(), changed)
        .getOrElseThrow(error -> new BusinessException("推进工作流投影游标失败", error));
    if (!Long.valueOf(1).equals(rows)) { throw new BusinessException("工作流投影游标未更新唯一目标"); }
    cursor.setInstanceId(event.getInstanceId());
    cursor.setApprovalCaseId(event.getApprovalCaseId());
    cursor.setSubmissionRound(event.getSubmissionRound());
    cursor.setStateSequence(event.getStateSequence());
    cursor.setLastEventFactId(event.getEventFactId());
  }

  private Optional<WorkflowBusinessFactCursor> lock(String projectionKey) {
    var model = cursors().getDataModel();
    var query = QueryStatement.builder().from(model.getFullName(), model.getRawName())
        .select(model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(SExpression.create(Operators.EQ, SExpression.field("projectionKey"), SExpression.constant(projectionKey))).build();
    var rows = model.queryForUpdate(query).getOrElseThrow(error -> new BusinessException("锁定工作流投影游标失败", error));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().toBean(WorkflowBusinessFactCursor.class));
  }

  private TypedDataModel<String, WorkflowBusinessFactCursor> cursors() {
    return TypedDataModelCenter.getDataModel(String.class, WorkflowBusinessFactCursor.class)
        .orElseThrow(() -> new BusinessException("工作流投影游标模型不可用"));
  }

  private TypedDataModel<String, WorkflowBusinessFactReceipt> receipts() {
    return TypedDataModelCenter.getDataModel(String.class, WorkflowBusinessFactReceipt.class)
        .orElseThrow(() -> new BusinessException("工作流事实接收模型不可用"));
  }

  private String receiptKey(WorkflowBusinessFactCursor cursor, String sourceFactId) {
    return key(List.of(cursor.getProjectionKey(), sourceFactId));
  }

  private String key(List<String> identity) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(Json.toJsonString(identity).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("JVM 缺少 SHA-256 算法", failure);
    }
  }

  private void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new BusinessException("业务工作流投影必须参与接收事务");
    }
  }
}
