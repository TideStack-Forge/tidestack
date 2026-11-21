package com.ouroboros.workflow.approval.external.wecom;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.ouroboros.enterprise.callback.EnterpriseCallbackSubscriber;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService;

/** Acknowledges only after the existing approval receipt/inbox accepts the event. */
@Component
public final class WecomApprovalCallbackSubscriber implements EnterpriseCallbackSubscriber {
  static final Set<String> EVENT_TYPES = Set.of("sys_approval_change");
  private final ExternalCallbackReceiptService receipts;

  public WecomApprovalCallbackSubscriber(ExternalCallbackReceiptService receipts) { this.receipts = receipts; }
  @Override public String subscriberId() { return "approval.wecom"; }
  @Override public String provider() { return "wecom"; }
  @Override public Set<String> eventTypes() { return EVENT_TYPES; }

  @Override
  public void receive(VerifiedEnterpriseEvent event) {
    var envelope = WecomCallbackCodec.translate(event);
    var accepted = receipts.receive(WecomEngineAdapterProvider.ADAPTER_ID, event.connectionId(),
        event.eventId(), envelope.payloadDigest(), List.of(envelope));
    if (accepted.isFailure()) { throw new BusinessException("审批回调未持久受理", accepted.getCause()); }
  }
}
