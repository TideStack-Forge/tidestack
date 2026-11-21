package com.ouroboros.app.apiresult;

import jakarta.annotation.Priority;

import io.vavr.Tuple3;

import com.ouroboros.app.error.ErrorResponseResolver;
import com.ouroboros.web.DefaultWebApiResultExceptionMapper;

@Priority(10)
public class EnhanceExceptionMapper extends DefaultWebApiResultExceptionMapper {
  private final ErrorResponseResolver errorResponseResolver;

  public EnhanceExceptionMapper() {
    this(null);
  }

  EnhanceExceptionMapper(ErrorResponseResolver errorResponseResolver) {
    this.errorResponseResolver = errorResponseResolver;
  }

  @Override
  public Tuple3<Integer, String, Object> map(Throwable throwable) {
    return resolveErrorResponseResolver().resolve(throwable);
  }

  private ErrorResponseResolver resolveErrorResponseResolver() {
    return errorResponseResolver == null ? ErrorResponseResolver.current() : errorResponseResolver;
  }
}
