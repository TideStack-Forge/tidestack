package com.ouroboros.enterprise;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Typed provider credential. Its immutable application identity distinguishes rotation from replacement. */
public interface EnterpriseCredential {
  @JsonIgnore
  Map<String, String> applicationIdentity();
}
