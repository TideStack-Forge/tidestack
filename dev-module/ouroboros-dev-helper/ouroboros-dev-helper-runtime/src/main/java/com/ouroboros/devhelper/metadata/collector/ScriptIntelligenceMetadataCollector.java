package com.ouroboros.devhelper.metadata.collector;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataSourceType;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.script.intelligence.RuntimeScriptIntelligenceSnapshotFactory;
import com.ouroboros.script.intelligence.impl.ClassGraphJavaTypeMetadataIndex;
import com.ouroboros.script.intelligence.impl.SpiScriptContextCatalog;
import com.ouroboros.util.Json;

public class ScriptIntelligenceMetadataCollector implements MetadataCollector {
  public static final String METADATA_TYPE = "script-intelligence";

  private final RuntimeScriptIntelligenceSnapshotFactory snapshotFactory;

  public ScriptIntelligenceMetadataCollector() {
    this(new RuntimeScriptIntelligenceSnapshotFactory(
        new SpiScriptContextCatalog(),
        new ClassGraphJavaTypeMetadataIndex()
    ));
  }

  public ScriptIntelligenceMetadataCollector(RuntimeScriptIntelligenceSnapshotFactory snapshotFactory) {
    this.snapshotFactory = snapshotFactory;
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return Arrays.asList("javascript", "groovy", "spel").stream()
        .map(this::toMetadata)
        .collect(Collectors.toList());
  }

  private Metadata toMetadata(String language) {
    var snapshot = "spel".equals(language)
        ? new com.ouroboros.expression.intelligence.ExpressionIntelligenceSnapshotFactory().build("dev-helper-runtime")
        : snapshotFactory.build(language, "dev-helper-runtime");
    var metadata = new Metadata();
    metadata.setName(language);
    metadata.setType(METADATA_TYPE);
    metadata.setLabel(snapshot.getRuntimeProfile());
    metadata.setPriority(Integer.MAX_VALUE);
    metadata.setSourceClassName(getClass().getName());
    metadata.setSourceType(MetadataSourceType.PRO_CODE);
    metadata.setDetails(Json.toMap(Json.toJsonString(snapshot)));
    return metadata;
  }
}
