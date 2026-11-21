package com.ouroboros.logicflow.adapter;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.ServerRequest;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.logicflow.LogicFlowExecutionGateException;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.*;

class HandlerFunctionAdapterTest {
  @Test
  void failuresUseSharedHttpStatusAndSafePayloadAndRollback() throws Exception {
    for (var failure : List.of(new InternalException("PRIVATE_SQL_DIAGNOSTIC"),
        new BusinessException(40301, "无权限"), new LogicFlowExecutionGateException("逻辑流不可执行"))) {
      var transactionManager = new RecordingTransactionManager();
      var handler = HandlerFunctionAdapter.buildHandler("test", transactionManager,
          (name, inputs) -> Try.failure(failure));
      var response = handler.handle(ServerRequest.create(new MockHttpServletRequest("GET", "/test"), List.of()));
      var result = (WebApiResult<?>) ((EntityResponse<?>) response).entity();
      assertEquals(failure instanceof LogicFlowExecutionGateException ? 400 : failure instanceof InternalException ? 500 : 403,
          response.statusCode().value());
      assertFalse(result.getMsg().contains("PRIVATE_SQL_DIAGNOSTIC"));
      if (failure instanceof InternalException && !(failure instanceof LogicFlowExecutionGateException)) {
        assertEquals(50000, result.getCode());
        assertEquals("服务暂时不可用，请稍后重试", result.getMsg());
      }
      assertTrue(transactionManager.rolledBack);
      assertFalse(transactionManager.committed);
    }
  }

  @Test
  void successStillCommitsAndReturns200() throws Exception {
    var transactionManager = new RecordingTransactionManager();
    var response = HandlerFunctionAdapter.buildHandler("test", transactionManager,
        (name, inputs) -> Try.success("ok")).handle(
            ServerRequest.create(new MockHttpServletRequest("GET", "/test"), List.of()));
    assertEquals(200, response.statusCode().value());
    assertTrue(transactionManager.committed);
    assertFalse(transactionManager.rolledBack);
  }

  private static class RecordingTransactionManager implements PlatformTransactionManager {
    private boolean committed;
    private boolean rolledBack;

    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }
    public void commit(TransactionStatus status) {
      committed = true;
    }
    public void rollback(TransactionStatus status) {
      rolledBack = true;
    }
  }
}
