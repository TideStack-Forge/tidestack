package com.ouroboros.data.runtime.adapter;

import jakarta.annotation.Priority;
import org.apache.commons.lang3.ObjectUtils;
import io.vavr.Tuple;
import io.vavr.Tuple3;

import com.ouroboros.data.exception.DataValidationException;
import com.ouroboros.data.exception.RecordValidationException;
import com.ouroboros.web.DefaultWebApiResultExceptionMapper;

/** 数据引擎异常在平台 Web 边界的映射，保留既有响应语义。 */
@Priority(10)
public class DataWebApiResultExceptionMapper extends DefaultWebApiResultExceptionMapper {
  @Override
  public Tuple3<Integer, String, Object> map(Throwable throwable) {
    if (throwable instanceof RecordValidationException exception) {
      var errors = exception.getJoinedErrors(",");
      Object finalErrors = ObjectUtils.isEmpty(errors) ? exception.getMessage() : errors;
      return Tuple.of(-1, "", finalErrors);
    }
    if (throwable instanceof DataValidationException exception) {
      return Tuple.of(-1, exception.getMessage(), null);
    }
    return super.map(throwable);
  }
}
