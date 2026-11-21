package com.ouroboros.logicflow.manage.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

@RestControllerAdvice(assignableTypes = {RuntimeFlowController.class, RuntimeFlowCatalogController.class, FlowObservationController.class})
public class RuntimeFlowHttpErrors {
  @ExceptionHandler(BusinessException.class)
  public WebApiResult<Void> failure(BusinessException failure, HttpServletResponse response) {
    int status = failure.getErrorCode();
    response.setStatus(status >= 400 && status < 600 ? status : 500);
    return WebApiResult.error(status, failure.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public WebApiResult<Void> invalidBody(HttpMessageNotReadableException failure, HttpServletResponse response) {
    response.setStatus(400);
    return WebApiResult.error(400, "逻辑流命令格式不合法或包含未知字段");
  }
}
