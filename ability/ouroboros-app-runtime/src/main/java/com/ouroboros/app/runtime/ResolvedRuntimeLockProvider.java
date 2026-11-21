package com.ouroboros.app.runtime;

import java.util.Map;
import java.util.Optional;

public interface ResolvedRuntimeLockProvider {
  Optional<Map<String, Object>> findLatestResolvedArtifactInput();
}
