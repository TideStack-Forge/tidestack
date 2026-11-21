package com.ouroboros.app.error;

import com.ouroboros.errorcode.ResolvedErrorDescriptor;
import com.ouroboros.errorcode.ThrowableErrorDescriptorMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.web.DefaultWebApiResultExceptionMapper;

public class DefaultThrowableErrorDescriptorMapper implements ThrowableErrorDescriptorMapper {
  private static final int VALIDATION_ERROR_CODE = 422;
  private static final String VALIDATION_FALLBACK_MESSAGE = "校验失败";
  static final String DEFAULT_FALLBACK_MESSAGE = "操作失败";

  @Override
  public boolean supports(Throwable throwable) {
    return true;
  }

  @Override
  public ResolvedErrorDescriptor map(Throwable throwable) {
    if (throwable instanceof ValidationException validationException) {
      return ResolvedErrorDescriptor.builder()
          .code(validationException.getErrorCode())
          .fallbackMessageTemplate(isBlank(validationException.getMessage()) ? VALIDATION_FALLBACK_MESSAGE : validationException.getMessage())
          .originalMessage(validationException.getMessage())
          .errors(validationException.getErrors())
          .build();
    }

    if (throwable instanceof com.ouroboros.data.exception.RecordValidationException recordValidationException) {
      return ResolvedErrorDescriptor.builder()
          .code(VALIDATION_ERROR_CODE)
          .fallbackMessageTemplate(VALIDATION_FALLBACK_MESSAGE)
          .originalMessage(recordValidationException.getMessage())
          .errors(recordValidationException.getJoinedErrors(","))
          .build();
    }

    if (throwable instanceof com.ouroboros.data.exception.DataValidationException dataValidationException) {
      return ResolvedErrorDescriptor.builder()
          .code(VALIDATION_ERROR_CODE)
          .fallbackMessageTemplate(isBlank(dataValidationException.getMessage()) ? VALIDATION_FALLBACK_MESSAGE : dataValidationException.getMessage())
          .originalMessage(dataValidationException.getMessage())
          .build();
    }

    if (throwable instanceof BusinessException ouroborosException) {
      var builder = ResolvedErrorDescriptor.builder()
          .code(ouroborosException.getErrorCode())
          .originalMessage(ouroborosException.getMessage());
      if (ouroborosException.getErrorCodeReference() != null) {
        builder.bizCode(ouroborosException.getErrorCodeReference().getBizCode());
        builder.fallbackMessageTemplate(ouroborosException.getErrorCodeReference().getFallbackMessageTemplate());
        builder.messageArgs(ouroborosException.getMessageArgs());
      } else {
        builder.fallbackMessageTemplate(ouroborosException.getMessage());
      }
      return builder.build();
    }

    var fallback = new DefaultWebApiResultExceptionMapper().map(throwable);
    return ResolvedErrorDescriptor.builder()
        .code(fallback._1)
        .fallbackMessageTemplate(fallback._2)
        .errors(fallback._3)
        .build();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
