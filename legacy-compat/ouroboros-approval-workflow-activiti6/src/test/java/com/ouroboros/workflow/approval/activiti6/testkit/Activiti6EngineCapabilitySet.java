package com.ouroboros.workflow.approval.activiti6.testkit;

import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;

public final class Activiti6EngineCapabilitySet {
  public static final EngineCapability DEFAULT = new EngineCapability.DefaultEngineCapability(
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.DEFERRED,
      EngineCapability.CapabilityLevel.DEFERRED,
      EngineCapability.CapabilityLevel.DEFERRED,
      EngineCapability.CapabilityLevel.DEFERRED,
      EngineCapability.CapabilityLevel.DEFERRED,
      EngineCapability.CapabilityLevel.DEFERRED,
      EngineCapability.CapabilityLevel.COMPATIBLE,
      EngineCapability.CapabilityLevel.DEFERRED
  );

  private Activiti6EngineCapabilitySet() {
  }
}
