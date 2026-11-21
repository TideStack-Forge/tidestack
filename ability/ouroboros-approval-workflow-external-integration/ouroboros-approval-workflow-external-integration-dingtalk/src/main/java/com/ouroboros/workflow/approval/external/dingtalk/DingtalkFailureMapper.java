package com.ouroboros.workflow.approval.external.dingtalk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalOperationFailure;

/**
 * DingTalk provider failures are normalized at the provider boundary.
 *
 * <p>The mapper deliberately keeps response bodies out of messages. A numeric provider error
 * code is retained when one is present, while credentials and business payloads are never copied
 * into an Ouroboros exception or a log message.
 */
final class DingtalkFailureMapper {

  private static final Pattern PROVIDER_CODE = Pattern.compile(
      "\\\"(?:errcode|code|errorCode)\\\"\\s*:\\s*(?:\\\"([^\\\"]+)\\\"|(-?\\d+))");

  private DingtalkFailureMapper() {
  }

  /**
   * 有副作用的调用（发起审批、任务动作）：超时/5xx 时远端可能已经生效，结果未知。
   */
  static RuntimeException remote(String operation, Exception cause) {
    return remote(operation, cause, true);
  }

  /**
   * 归一化远端失败。
   *
   * <p>{@code sideEffect} 决定"结果确定性"的判定，必须与企微、飞书保持一致口径：
   * 只有可能有副作用的调用（发起、任务动作）在超时/408/429/5xx 时才是
   * {@link ExternalOperationFailure#unknown}；只读调用（token 换取、schema 查询、实例详情查询）
   * 失败必然意味着远端未生效，应返回"确定未生效"而不是"结果未知"——
   * 此前所有调用一视同仁地标 UNKNOWN，使同一契约在钉钉与另两个渠道给出不同判定。
   */
  static RuntimeException remote(String operation, Exception cause, boolean sideEffect) {
    if (cause instanceof OuroborosException ex) {
      return ex;
    }
    if (cause instanceof HttpStatusCodeException http) {
      String providerCode = providerCode(http.getResponseBodyAsString());
      String suffix = providerCode == null ? "" : ", providerErrorCode=" + providerCode;
      int status = http.getStatusCode().value();
      if (sideEffect && (status == 408 || status == 429 || status >= 500)) {
        return ExternalOperationFailure.unknown(
            "钉钉 " + operation + " 结果未知: httpStatus=" + status + suffix, cause);
      }
      return new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] 钉钉 "
              + operation + " 被远端拒绝: httpStatus=" + status + suffix,
          cause);
    }
    if (cause instanceof ResourceAccessException
        || cause instanceof java.net.SocketTimeoutException
        || cause instanceof java.util.concurrent.TimeoutException) {
      if (sideEffect) {
        return ExternalOperationFailure.unknown(
            "钉钉 " + operation + "请求失败：请求超时，远端结果未知, causeType="
                + cause.getClass().getSimpleName(), cause);
      }
      return ExternalOperationFailure.definitelyNotApplied(
          "钉钉 " + operation + " 请求超时（只读调用，远端未生效）, causeType="
              + cause.getClass().getSimpleName(), cause);
    }
    return new InternalException(
        "钉钉 " + operation + " 请求失败, causeType=" + cause.getClass().getSimpleName(), cause);
  }

  static RuntimeException malformedResponse(String operation, Exception cause) {
    return new InternalException("钉钉 " + operation + " 响应格式非法", cause);
  }

  static RuntimeException providerRejected(String operation, String providerCode) {
    String suffix = providerCode == null || providerCode.isBlank()
        ? "" : ", providerErrorCode=" + providerCode;
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] 钉钉 "
            + operation + " 被远端拒绝" + suffix);
  }

  static RuntimeException callbackRejected(String operation, Exception cause) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN + "] 钉钉 "
            + operation + " 验证或解析失败",
        cause);
  }

  private static String providerCode(String body) {
    if (body == null) {
      return null;
    }
    Matcher matcher = PROVIDER_CODE.matcher(body);
    return matcher.find() ? (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)) : null;
  }
}
