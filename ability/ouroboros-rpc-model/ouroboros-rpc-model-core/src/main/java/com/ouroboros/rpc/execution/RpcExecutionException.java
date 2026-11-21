package com.ouroboros.rpc.execution;

import com.ouroboros.rpc.exception.RPCException;

/** Stable failure stage, independent of transport exception messages. */
public class RpcExecutionException extends RPCException {
  private final String stage;

  public RpcExecutionException(String stage, String message, Throwable cause) {
    super(message, cause);
    this.stage = stage;
  }

  public String getStage() { return stage; }
}
