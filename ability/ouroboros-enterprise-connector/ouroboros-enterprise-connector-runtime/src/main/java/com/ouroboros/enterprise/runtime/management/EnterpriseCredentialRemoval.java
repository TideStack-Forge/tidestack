package com.ouroboros.enterprise.runtime.management;

import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.exception.ValidationException;

/** Removes only unreferenced managed material under the original configuration lock. */
record EnterpriseCredentialRemoval(String connectionId) implements ConfigurationMutation {
  EnterpriseCredentialRemoval {
    if (connectionId == null || connectionId.isBlank()) {
      throw new ValidationException("请指定需要清除的凭据");
    }
  }
}
