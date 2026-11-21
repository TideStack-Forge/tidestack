package com.ouroboros.enterprise.runtime.management;

import java.util.Map;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.enterprise.runtime.credential.EncryptedCredential;

/** Constructed by the component editor; both protected rows receive the same command in one transaction. */
record EnterpriseConnectionMutation(Map<String, Object> connection, EncryptedCredential replacement,
                                            boolean acknowledgeDisable) implements ConfigurationMutation {
  public EnterpriseConnectionMutation { connection = Map.copyOf(connection); }
  @Override public String toString() { return "EnterpriseConnectionMutation[material=****]"; }
}
