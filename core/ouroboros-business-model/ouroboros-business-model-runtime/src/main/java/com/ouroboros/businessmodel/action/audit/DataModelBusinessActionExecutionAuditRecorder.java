package com.ouroboros.businessmodel.action.audit;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.action.BusinessActionExecutionAuditRecord;
import com.ouroboros.businessmodel.action.BusinessActionExecutionAuditRecorder;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

public class DataModelBusinessActionExecutionAuditRecorder implements BusinessActionExecutionAuditRecorder {
  private static final String MODEL_FULL_NAME = "ouroboros.BusinessActionExecutionAudit";

  private final PlatformTransactionManager transactionManager;

  public DataModelBusinessActionExecutionAuditRecorder(PlatformTransactionManager transactionManager) {
    this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
  }

  @Override
  public void begin(BusinessActionExecutionAuditRecord execution) {
    DataModel dataModel = auditDataModel();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.executeWithoutResult(status -> dataModel.insert(values(execution, true))
        .getOrElseThrow(error -> new BusinessException("业务 Action 执行审计初始化失败", error)));
  }

  @Override
  public void record(BusinessActionExecutionAuditRecord execution) {
    DataModel dataModel = auditDataModel();
    Map<String, Object> values = values(execution, false);
    values.remove("id");
    values.remove("startedAt");
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    int propagationBehavior = "COMMITTED".equals(execution.transactionResult())
        ? TransactionDefinition.PROPAGATION_REQUIRED
        : TransactionDefinition.PROPAGATION_REQUIRES_NEW;
    transaction.setPropagationBehavior(propagationBehavior);
    transaction.executeWithoutResult(status -> dataModel.update(execution.executionId(), values)
        .filter(rows -> rows > 0)
        .getOrElseThrow(error -> new BusinessException("业务 Action 执行审计完成状态写入失败", error)));
  }

  private DataModel auditDataModel() {
    return DataModelCenter.getDataModel(MODEL_FULL_NAME)
        .orElseThrow(() -> new BusinessException("业务 Action 执行审计模型不可用"));
  }

  private Map<String, Object> values(BusinessActionExecutionAuditRecord execution, boolean starting) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", execution.executionId());
    values.put("parentExecutionId", execution.parentExecutionId());
    values.put("modelFullName", execution.modelFullName());
    values.put("modelRevision", execution.modelRevision());
    values.put("actionName", execution.actionName());
    values.put("executionAuthorityName", execution.executionAuthorityName());
    values.put("implementationRef", execution.implementationRef());
    values.put("transactionResult", starting ? "STARTED" : execution.transactionResult());
    values.put("failureType", execution.failureType());
    values.put("actorRef", execution.actorRef());
    values.put("targetModelFullName", execution.target() == null ? null : execution.target().modelFullName());
    values.put("targetId", execution.target() == null ? null : execution.target().id());
    values.put("writeSetJson", Json.tryToJsonString(starting ? java.util.List.of() : execution.writeSet())
        .getOrElseThrow(error -> new BusinessException("业务 Action 写集无法序列化", error)));
    values.put("eventIdsJson", Json.tryToJsonString(starting ? java.util.List.of() : execution.eventIds())
        .getOrElseThrow(error -> new BusinessException("业务 Action 事件标识无法序列化", error)));
    values.put("startedAt", LocalDateTime.ofInstant(execution.startedAt(), ZoneOffset.UTC));
    values.put("completedAt", starting || execution.completedAt() == null
        ? null : LocalDateTime.ofInstant(execution.completedAt(), ZoneOffset.UTC));
    return values;
  }
}
