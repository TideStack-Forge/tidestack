package com.ouroboros.dev.codeintelligence;

import java.util.List;
import java.util.Map;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.metadata.MetadataSnapshotStatus;
import com.ouroboros.web.WebApiResult;
import org.springframework.web.bind.annotation.*;

/** Lightweight environment identity for IDE hosts that do not use the platform STOMP connection. */
@RestController
@RequestMapping("/api/dev/{appName}/code-intelligence/environment")
public final class CodeEnvironmentController {
  private final List<ApplicationMetadataSnapshotProvider> snapshots;
  public CodeEnvironmentController(List<ApplicationMetadataSnapshotProvider> snapshots) { this.snapshots = snapshots; }
  @GetMapping public WebApiResult<Object> environment(@PathVariable String appName) {
    for (var provider : snapshots) {
      var snapshot = provider.getApplicationMetadataSnapshot(appName);
      if (snapshot.getStatus() != MetadataSnapshotStatus.UNAVAILABLE)
        return WebApiResult.ok(Map.of("status", snapshot.getStatus().name(), "identity", snapshot.getRuntimeIdentity()));
    }
    return WebApiResult.ok(Map.of("status", "UNAVAILABLE"));
  }
}
