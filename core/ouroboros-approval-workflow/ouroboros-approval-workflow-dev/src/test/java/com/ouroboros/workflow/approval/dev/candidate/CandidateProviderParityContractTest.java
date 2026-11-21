package com.ouroboros.workflow.approval.dev.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.service.impl.BuiltinPositionDevelopService;
import com.ouroboros.dev.app.service.impl.BuiltinRoleDevelopService;
import com.ouroboros.workflow.approval.CandidateProvider;
import com.ouroboros.workflow.approval.provider.FormFieldCandidateProvider;
import com.ouroboros.workflow.approval.provider.PositionCandidateProvider;
import com.ouroboros.workflow.approval.provider.RoleCandidateProvider;
import com.ouroboros.workflow.approval.provider.StarterCandidateProvider;
import com.ouroboros.workflow.approval.provider.UserSelectCandidateProvider;

import static org.mockito.Mockito.mock;

class CandidateProviderParityContractTest {

  @Test
  void designTimeProvidersShouldMatchRuntimeNonEmployeeProviderKindConfigModeAndValueKey() {
    Map<String, ProviderMetadata> designTimeMetadata = toDesignTimeMetadataMap(Arrays.asList(
        new BuiltinRoleDesignTimeCandidateProvider(mock(BuiltinRoleDevelopService.class)),
        new BuiltinPositionDesignTimeCandidateProvider(mock(BuiltinPositionDevelopService.class)),
        new StarterDesignTimeCandidateProvider(),
        new FormFieldDesignTimeCandidateProvider(),
        new UserSelectDesignTimeCandidateProvider()
    ));
    Map<String, ProviderMetadata> runtimeMetadata = toMetadataMap(Arrays.asList(
        new RoleCandidateProvider(),
        new PositionCandidateProvider(),
        new StarterCandidateProvider(),
        new FormFieldCandidateProvider(),
        new UserSelectCandidateProvider()
    ));

    assertEquals(runtimeMetadata, designTimeMetadata);
  }

  private Map<String, ProviderMetadata> toDesignTimeMetadataMap(List<? extends ApprovalWorkflowDesignTimeCandidateProvider> providers) {
    Map<String, ProviderMetadata> metadataMap = new LinkedHashMap<>();
    for (ApprovalWorkflowDesignTimeCandidateProvider provider : providers) {
      metadataMap.put(provider.getName(), new ProviderMetadata(provider.getConfigMode(), provider.getValueKey()));
    }
    return metadataMap;
  }

  private Map<String, ProviderMetadata> toMetadataMap(List<? extends CandidateProvider> providers) {
    Map<String, ProviderMetadata> metadataMap = new LinkedHashMap<>();
    for (CandidateProvider provider : providers) {
      metadataMap.put(provider.getName(), new ProviderMetadata(provider.getConfigMode(), provider.getValueKey()));
    }
    return metadataMap;
  }

  private static final class ProviderMetadata {
    private final CandidateProvider.ConfigMode configMode;
    private final String valueKey;

    private ProviderMetadata(CandidateProvider.ConfigMode configMode, String valueKey) {
      this.configMode = configMode;
      this.valueKey = valueKey;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof ProviderMetadata)) {
        return false;
      }
      ProviderMetadata that = (ProviderMetadata) other;
      return configMode == that.configMode && java.util.Objects.equals(valueKey, that.valueKey);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(configMode, valueKey);
    }
  }
}
