package com.ouroboros.workflow.approval.activiti6.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ouroboros.workflow.approval.testkit.adapter.EngineCapability;
import org.junit.jupiter.api.Test;

class Activiti6EngineCapabilitySetTest {
  @Test
  void defaultMarksSupportedContractsAsCompatible() {
    assertSame(EngineCapability.CapabilityLevel.COMPATIBLE, Activiti6EngineCapabilitySet.DEFAULT.semanticRoundTripLevel());
    assertSame(EngineCapability.CapabilityLevel.COMPATIBLE, Activiti6EngineCapabilitySet.DEFAULT.childSubflowParentMetadataLevel());
    assertTrue(Activiti6EngineCapabilitySet.DEFAULT.supportsSemanticRoundTrip());
    assertTrue(Activiti6EngineCapabilitySet.DEFAULT.supportsChildSubflowParentMetadata());
  }

  @Test
  void defaultKeepsWorkflowEnhancementCapabilitiesDeferred() {
    assertSame(EngineCapability.CapabilityLevel.DEFERRED, Activiti6EngineCapabilitySet.DEFAULT.taskTransferLevel());
    assertSame(EngineCapability.CapabilityLevel.DEFERRED, Activiti6EngineCapabilitySet.DEFAULT.freeApprovalLevel());
    assertSame(EngineCapability.CapabilityLevel.DEFERRED, Activiti6EngineCapabilitySet.DEFAULT.taskTimeoutPolicyLevel());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsTaskTransfer());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsAddSign());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsConfigurableRejectTarget());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsFreeRejection());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsFreeRecovery());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsFreeApproval());
    assertFalse(Activiti6EngineCapabilitySet.DEFAULT.supportsTaskTimeoutPolicy());
  }
}
