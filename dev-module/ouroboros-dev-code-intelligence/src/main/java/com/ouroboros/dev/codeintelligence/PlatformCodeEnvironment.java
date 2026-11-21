package com.ouroboros.dev.codeintelligence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.ouroboros.expression.intelligence.ExpressionIntelligenceSnapshotFactory;
import com.ouroboros.script.intelligence.RuntimeScriptIntelligenceSnapshotFactory;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.script.intelligence.impl.ClassGraphJavaTypeMetadataIndex;
import com.ouroboros.script.intelligence.impl.SpiScriptContextCatalog;
import com.ouroboros.util.Json;

/** Reference symbols only. These descriptors must never supply an application's language-server classpath. */
final class PlatformCodeEnvironment {
  private final RuntimeScriptIntelligenceSnapshotFactory scripts = new RuntimeScriptIntelligenceSnapshotFactory(
      new SpiScriptContextCatalog(), new ClassGraphJavaTypeMetadataIndex());
  private final Map<String, String> snapshots = new ConcurrentHashMap<>();

  ScriptIntelligenceSnapshot describe(String language) {
    if (!java.util.Set.of("spel", "groovy", "javascript").contains(language)) return null;
    var encoded = snapshots.computeIfAbsent(language, profile -> Json.toJsonString("spel".equals(profile)
        ? new ExpressionIntelligenceSnapshotFactory().build("dev-platform-fallback")
        : scripts.build(profile, "dev-platform-fallback")));
    return Json.toBean(ScriptIntelligenceSnapshot.class, encoded);
  }
}
