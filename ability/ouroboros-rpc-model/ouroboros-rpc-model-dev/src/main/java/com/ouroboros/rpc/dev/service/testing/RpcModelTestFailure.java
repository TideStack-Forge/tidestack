package com.ouroboros.rpc.dev.service.testing;

import com.ouroboros.exception.BusinessException;

final class RpcModelTestFailure extends BusinessException {
  private final String stage;
  private final String reason;
  private final String resource;
  private final String field;

  RpcModelTestFailure(String stage, String reason, String message, String resource, String field) {
    super(message);
    this.stage = stage; this.reason = reason; this.resource = resource; this.field = field;
  }

  RpcModelTestResult.Error toError() {
    return new RpcModelTestResult.Error(stage, reason, getMessage(), resource, field, true);
  }
}
