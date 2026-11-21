package com.ouroboros.workflow.approval.external.dingtalk;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.ouroboros.enterprise.callback.EnterpriseCallbackSubscriber;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService;

/** Acknowledges only after the existing approval receipt/inbox accepts the event. */
@Component
public final class DingtalkApprovalCallbackSubscriber implements EnterpriseCallbackSubscriber {
  static final Set<String> EVENT_TYPES = Set.of("bpms_instance_change", "bpms_task_change");
  private final ExternalCallbackReceiptService receipts;

  public DingtalkApprovalCallbackSubscriber(ExternalCallbackReceiptService receipts) { this.receipts = receipts; }
  @Override public String subscriberId() { return "approval.dingtalk"; }
  @Override public String provider() { return "dingtalk"; }
  @Override public Set<String> eventTypes() { return EVENT_TYPES; }

  @Override
  public void receive(VerifiedEnterpriseEvent event) {
    var envelope = DingtalkCallbackCodec.translate(event);
    var accepted = receipts.receive(DingtalkEngineAdapterProvider.ADAPTER_ID, event.connectionId(),
        event.eventId(), envelope.payloadDigest(), List.of(envelope));
    if (accepted.isFailure()) { throw new BusinessException("审批回调未持久受理", accepted.getCause()); }
  }
}
