package com.ouroboros.enterprise.runtime.management;

import com.ouroboros.configuration.management.ConfigurationMutation;

/** Rewrap existing material with the deployment's active key; does not replace application credentials. */
public enum EnterpriseCredentialReencryption implements ConfigurationMutation {
  INSTANCE
}
