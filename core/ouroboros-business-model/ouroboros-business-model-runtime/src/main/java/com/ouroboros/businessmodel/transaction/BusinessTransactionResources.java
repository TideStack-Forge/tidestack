package com.ouroboros.businessmodel.transaction;

import java.util.Objects;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelWriteGuard;
import com.ouroboros.exception.BusinessException;

/** Verifies writes against the actual commit resource and rejects independent inner commits. */
public final class BusinessTransactionResources implements DataModelWriteGuard {
  private final TransactionStatus status;
  private final TransactionSynchronization boundary;
  private final Object commitResource;

  private BusinessTransactionResources(TransactionStatus status, TransactionSynchronization boundary, Object commitResource) {
    this.status = status;
    this.boundary = boundary;
    this.commitResource = commitResource;
  }

  public static BusinessTransactionResources capture(PlatformTransactionManager manager, TransactionStatus status) {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(status);
    TransactionSynchronization boundary = null;
    Object resource = null;
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      boundary = new TransactionSynchronization() { };
      TransactionSynchronizationManager.registerSynchronization(boundary);
      if (manager instanceof DataSourceTransactionManager local && local.getDataSource() != null
          && TransactionSynchronizationManager.hasResource(local.getDataSource())) {
        var connection = DataSourceUtils.getConnection(local.getDataSource());
        try {
          resource = connection;
        } finally {
          DataSourceUtils.releaseConnection(connection, local.getDataSource());
        }
      }
    }
    return new BusinessTransactionResources(status, boundary, resource);
  }

  @Override
  public void markRollbackOnly() {
    status.setRollbackOnly();
  }

  @Override
  public void beforeWrite(DataModel model) {
    try {
      if (commitResource == null) {
        throw new BusinessException("当前事务管理器的资源参与方式尚未通过可靠写入验证");
      }
      if (!TransactionSynchronizationManager.isActualTransactionActive()
          || !TransactionSynchronizationManager.isSynchronizationActive()
          || !TransactionSynchronizationManager.getSynchronizations().contains(boundary)
          || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
        throw new BusinessException("业务写入必须参与拥有提交责任的同一可写事务");
      }
      Object actual = model.getAdapter().requireTransactionResource()
          .getOrElseThrow(error -> new BusinessException("数据模型无法证明实际事务参与: " + model.getFullName(), error));
      if (actual != commitResource) {
        throw new BusinessException("数据模型资源未参与统一提交边界: " + model.getFullName());
      }
    } catch (RuntimeException error) {
      markRollbackOnly();
      throw error;
    }
  }
}
