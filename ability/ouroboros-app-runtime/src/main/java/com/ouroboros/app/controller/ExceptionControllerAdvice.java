package com.ouroboros.app.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ouroboros.app.error.ErrorResponseResolver;
import com.ouroboros.data.exception.DataValidationException;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebRequestLogPath;

/**
 * @author liansz
 */
@RestControllerAdvice
@SuppressWarnings("unused")
public class ExceptionControllerAdvice {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ErrorResponseResolver errorResponseResolver;

  public ExceptionControllerAdvice() {
    this(null);
  }

  ExceptionControllerAdvice(ErrorResponseResolver errorResponseResolver) {
    this.errorResponseResolver = errorResponseResolver;
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, MethodArgumentTypeMismatchException e) {
    logError(request, e);
    return buildErrorResponse(request, e);
  }

  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, HttpRequestMethodNotSupportedException e) {
    logError(request, e);
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(com.ouroboros.data.exception.ValidationException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, com.ouroboros.data.exception.ValidationException e) {
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(DataValidationException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, DataValidationException e) {
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(ValidationException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, ValidationException e) {
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(BusinessException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, BusinessException e) {
    logWarn(request, e);
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(PlatformException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, PlatformException e) {
    logError(request, e);
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(InternalException.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, InternalException e) {
    logError(request, e);
    return buildErrorResponse(request, e);
  }

  @ExceptionHandler(Throwable.class)
  public WebApiResult<?> exceptionHandler(HttpServletRequest request, Throwable e) {
    logError(request, e);
    return buildErrorResponse(request, e);
  }

  /**
   * 打印日志
   *
   * @param request 请求
   * @param e 异常
   */
  private void logWarn(HttpServletRequest request, Throwable e) {
    logger.warn("接口 {}:{} 请求失败: ", request.getMethod(), WebRequestLogPath.resolve(request), e);
  }

  private void logError(HttpServletRequest request, Throwable e) {
    logger.error("接口 {}:{} 请求错误: ", request.getMethod(), WebRequestLogPath.resolve(request), e);
  }

  private WebApiResult<?> buildErrorResponse(HttpServletRequest request, Throwable throwable) {
    var tuple = resolveErrorResponseResolver().resolve(
        throwable,
        request == null ? null : request.getHeader("Accept-Language")
    );
    WebApiResult<Object> result = WebApiResult.error(tuple._1, tuple._2);
    if (tuple._3 != null) {
      result.setErrors(tuple._3);
    }
    return result;
  }

  private ErrorResponseResolver resolveErrorResponseResolver() {
    return errorResponseResolver == null ? ErrorResponseResolver.current() : errorResponseResolver;
  }
}
