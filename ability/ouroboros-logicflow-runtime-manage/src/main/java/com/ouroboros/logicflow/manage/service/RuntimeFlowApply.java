package com.ouroboros.logicflow.manage.service;

/** Queue latest-state reconciliation; requests are hints, persisted state remains authoritative. */
public interface RuntimeFlowApply {
  String instanceId();
  void request();
}
