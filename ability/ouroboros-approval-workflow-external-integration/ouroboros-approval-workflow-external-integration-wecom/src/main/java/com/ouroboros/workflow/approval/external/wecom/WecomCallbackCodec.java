package com.ouroboros.workflow.approval.external.wecom;

import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.enterprise.wecom.WecomCallbackProtocol;
import com.ouroboros.enterprise.wecom.WecomCredential;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import io.vavr.control.Try;

/** Approval translation; encrypted framing and validation stay in the connector. */
public final class WecomCallbackCodec implements ExternalCallbackCodec {
  private final ResolvedEnterpriseConnection connection;

  public WecomCallbackCodec(ResolvedEnterpriseConnection connection, WecomCredential credential) {
    this.connection = connection;
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    return Try.of(() -> {
      if (!connection.connectionId().equals(request.connectionId())) { throw new ValidationException("回调连接不匹配"); }
      return new WecomCallbackProtocol().verify(connection,
          new EnterpriseCallbackRequest("POST", Map.of(), request.headers(), request.body()))
          .events().stream().map(WecomCallbackCodec::translate).toList();
    }).recoverWith(Exception.class, ex -> Try.failure(WecomFailureMapper.callbackRejected(ex)));
  }

  static ExternalCallbackEnvelope translate(VerifiedEnterpriseEvent verified) {
    try {
      if (!"wecom".equals(verified.provider()) || !WecomApprovalCallbackSubscriber.EVENT_TYPES.contains(verified.eventType())) {
        throw new ValidationException("非企业微信审批事件");
      }
      String plain = verified.payload();
      Document event = WecomCallbackProtocol.parseXml(plain);
      String approvalNumber = firstText(event, "SpNoStr", "SpNo");
      String status = xmlText(event, "SpStatus");
      String changeType = optionalText(event, "StatuChangeEvent");
      String digest = ExternalRouteKeys.of("wecom-callback-payload", plain);
      String resultCode = normalizeStatus(status);
      String eventId = ExternalRouteKeys.of(
          "wecom-approval-event", approvalNumber, status, String.valueOf(changeType), digest);
      ExternalCallbackEnvelope envelope = new ExternalCallbackEnvelope(
          eventId,
          WecomEngineAdapterProvider.ADAPTER_ID,
          verified.connectionId(),
          approvalNumber,
          null,
          null,
          null,
          "sys_approval_change/" + (changeType == null ? "status" : changeType),
          resultCode,
          digest);
      return envelope;
    } catch (Exception ex) { throw new com.ouroboros.exception.BusinessException("企业微信审批回调解析失败", ex); }
  }

  private static String normalizeStatus(String status) {
    return switch (status) {
      case "1" -> "RUNNING";
      case "2" -> "APPROVED";
      case "3" -> "REJECTED";
      case "4" -> "CANCELED";
      case "6" -> "WECOM_REVOKED_AFTER_APPROVAL";
      case "7" -> "WECOM_DELETED";
      case "10" -> "WECOM_PAID";
      default -> "WECOM_STATUS_" + status;
    };
  }

  private static String firstText(Document document, String... names) {
    for (String name : names) {
      String value = optionalText(document, name);
      if (value != null) {
        return value;
      }
    }
    throw new IllegalStateException("企业微信审批回调缺少审批单号");
  }

  private static String xmlText(Document document, String name) {
    String value = optionalText(document, name);
    if (value == null) {
      throw new IllegalStateException("企业微信回调缺少 " + name);
    }
    return value;
  }

  private static String optionalText(Document document, String name) {
    return WecomCallbackProtocol.text(document, name);
  }
}
