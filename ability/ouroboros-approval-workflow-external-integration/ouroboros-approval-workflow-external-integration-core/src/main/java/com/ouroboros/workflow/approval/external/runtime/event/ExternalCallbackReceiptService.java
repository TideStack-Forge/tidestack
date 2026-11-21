package com.ouroboros.workflow.approval.external.runtime.event;

import java.util.List;

import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;

import io.vavr.control.Try;

/** Single durable receipt boundary shared by every verified callback transport. */
public interface ExternalCallbackReceiptService {

  Try<Void> receive(
      String adapterId,
      String connectionId,
      String nonce,
      String payloadDigest,
      List<ExternalCallbackEnvelope> envelopes);
}
