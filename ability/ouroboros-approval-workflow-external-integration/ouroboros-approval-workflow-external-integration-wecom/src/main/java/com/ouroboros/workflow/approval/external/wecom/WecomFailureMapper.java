package com.ouroboros.workflow.approval.external.wecom;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;

/** Normalizes provider failures without copying credentials or payloads into messages. */
final class WecomFailureMapper {

  private WecomFailureMapper() {
  }

  static RuntimeException remote(String operation, Exception cause, boolean sideEffect) {
    if (cause instanceof OuroborosException ex) {
      return ex;
    }
    if (cause instanceof HttpStatusCodeException http) {
      int status = http.getStatusCode().value();
      if (sideEffect && (status == 408 || status == 429 || status >= 500)) {
        return ExternalOperationFailure.unknown(
            "企业微信 " + operation + " 结果未知: httpStatus=" + status, cause);
      }
      return new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] 企业微信 "
              + operation + " 被远端拒绝: httpStatus=" + status,
          cause);
    }
    if (cause instanceof ResourceAccessException
        || cause instanceof java.net.SocketTimeoutException
        || cause instanceof java.util.concurrent.TimeoutException) {
      if (sideEffect) {
        return ExternalOperationFailure.unknown(
            "企业微信 " + operation + " 请求超时，远端结果未知, causeType="
                + cause.getClass().getSimpleName(), cause);
      }
      return new InternalException(
          "企业微信 " + operation + " 请求超时, causeType="
              + cause.getClass().getSimpleName(), cause);
    }
    return new InternalException(
        "企业微信 " + operation + " 请求失败, causeType="
            + cause.getClass().getSimpleName(), cause);
  }

  static RuntimeException providerRejected(String operation, String providerCode) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] 企业微信 "
            + operation + " 被远端拒绝, providerErrorCode=" + providerCode);
  }

  static RuntimeException malformedResponse(String operation, Exception cause) {
    return new InternalException("企业微信 " + operation + " 响应格式非法", cause);
  }

  static RuntimeException callbackRejected(Exception cause) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN + "] 企业微信回调验证或解析失败",
        cause);
  }
}
