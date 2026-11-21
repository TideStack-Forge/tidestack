package com.ouroboros.logicflow.dev.service;

/** Platform-owned authentication and application/profile authorization. */
public interface LogicFlowDebugAccess {
  String currentOwner();

  boolean canDebug(String appName, String profile, String owner);
}
