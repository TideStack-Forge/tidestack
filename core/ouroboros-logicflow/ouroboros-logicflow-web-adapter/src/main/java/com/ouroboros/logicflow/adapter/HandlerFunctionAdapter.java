package com.ouroboros.logicflow.adapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.LogicFlowExecutionGateException;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatus;

public class HandlerFunctionAdapter {

  private static final Logger logger = LoggerFactory.getLogger(HandlerFunctionAdapter.class);

  public static HandlerFunction<ServerResponse> buildHandler(
      String flowName,
      PlatformTransactionManager platformTransactionManager,
      LogicFlowExecutionFacade executionFacade
  ) {
    return request -> {
      var requestInfo = RequestInfoMap.forRequest(request);

      // TODO: 先偷个懒，简单粗暴的加个事务管理，将来再考虑优化
      var def = new DefaultTransactionDefinition();
      def.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRED);
      var transactionStatus = platformTransactionManager.getTransaction(def);

      var result = executionFacade.execute(flowName, List.of(requestInfo));
      if (result.isFailure()) {
        platformTransactionManager.rollback(transactionStatus);
        var error = result.getCause();
        if (error instanceof LogicFlowExecutionGateException) {
          String message = error.getMessage();
          if (ObjectUtils.isEmpty(message)) {
            message = "逻辑流执行失败";
          }
          return ServerResponse.badRequest().body(WebApiResult.error(message));
        }
        if (!(error instanceof BusinessException)) {
          var textWriter = new StringWriter();
          error.printStackTrace(new PrintWriter(textWriter));
          logger.warn("逻辑流 {} 执行异常: {}\n完整栈输出：\n{}", flowName, error.getMessage(), textWriter);
        }
        var errorResult = WebApiResult.of(unwrapError(error));
        return ServerResponse.status(WebApiResultHttpStatus.resolve(errorResult.getCode())).body(errorResult);
      }

      platformTransactionManager.commit(transactionStatus);
      return ServerResponse.ok().body(WebApiResult.of(result));
    };
  }

  private static Throwable unwrapError(Throwable error) {
    if (error instanceof FlowRuntimeException && error.getCause() != null) {
      error = error.getCause();
    }
    if (error instanceof OuroborosException) {
      return error;
    }
    if (error.getCause() instanceof OuroborosException) {
      return error.getCause();
    }
    return error;
  }
}
