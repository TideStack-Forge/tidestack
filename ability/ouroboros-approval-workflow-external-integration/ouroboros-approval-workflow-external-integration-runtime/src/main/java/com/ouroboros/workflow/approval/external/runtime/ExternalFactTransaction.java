package com.ouroboros.workflow.approval.external.runtime;

import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.DataModelWriteGuard;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;

/** Runs platform-owned Saga completion on the journal's actual commit resource. */
public final class ExternalFactTransaction {
  private ExternalFactTransaction() { }

  public static <T> T execute(TransactionTemplate transaction, Class<?> journal, Supplier<T> work) {
    return DataModelExecutionContext.withPlatformWrite(() -> transaction.execute(status -> {
      Object resource = resource(journal);
      return DataModelExecutionContext.withWriteGuard(new DataModelWriteGuard() {
        public void beforeWrite(DataModel model) {
          Object actual = model.getAdapter().requireTransactionResource()
              .getOrElseThrow(error -> new BusinessException("外部审批写入未加入源事务", error));
          if (actual != resource) { throw new BusinessException("外部审批绑定、案件、源事实和日志不属于同一提交资源"); }
        }
        public void markRollbackOnly() { status.setRollbackOnly(); }
      }, work);
    }));
  }

  public static Object resource(Class<?> model) {
    return TypedDataModelCenter.getDataModel(model)
        .orElseThrow(() -> new BusinessException("外部审批源模型不可用: " + model.getSimpleName()))
        .getDataModel().getAdapter().requireTransactionResource()
        .getOrElseThrow(error -> new BusinessException("外部审批源模型未加入实际事务: " + model.getSimpleName(), error));
  }
}
