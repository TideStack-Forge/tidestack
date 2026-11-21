package com.ouroboros.devhelper.metadata.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.ouroboros.app.scriptcontext.SpringBeanFactoryScriptContextContributor;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.script.intelligence.JavaTypeMetadataIndex;
import com.ouroboros.script.intelligence.RuntimeScriptIntelligenceSnapshotFactory;
import com.ouroboros.script.intelligence.ScriptIntelligenceJavaType;

public class ScriptIntelligenceMetadataCollectorTest {
  private org.springframework.context.support.GenericApplicationContext context;

  @org.junit.Before
  public void startRuntimeContext() {
    context = new org.springframework.context.support.GenericApplicationContext();
    context.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    context.registerBean(com.ouroboros.app.scriptcontext.SpringBeanFactoryScriptContextContributor.class,
        () -> new com.ouroboros.app.scriptcontext.SpringBeanFactoryScriptContextContributor(context.getBeanFactory()));
    context.refresh();
  }

  @org.junit.After
  public void closeRuntimeContext() {
    context.close();
  }

  @Test
  public void getAllMetadata_emitsPerLanguageRuntimeSnapshots() {
    // The real script engine loads the application's Spring contributor through SPI.
    new SpringBeanFactoryScriptContextContributor(new DefaultListableBeanFactory());
    var wrappers = new LinkedHashMap<String, Object>();
    wrappers.put("Dates", FakeDates.class);

    var collector = new ScriptIntelligenceMetadataCollector(new RuntimeScriptIntelligenceSnapshotFactory(
        () -> wrappers,
        new StubJavaTypeMetadataIndex(Collections.singletonList("java.util.UUID"))
    ));

    var metadataList = collector.getAllMetadata();

    assertEquals("script-intelligence", collector.getMetadataType());
    assertTrue(findMetadata(metadataList, "javascript").isPresent());
    assertTrue(findMetadata(metadataList, "groovy").isPresent());

    var javascript = findMetadata(metadataList, "javascript").get();
    assertEquals("script-intelligence", javascript.getType());
    assertEquals("logicflow-graaljs", javascript.getDetails().get("runtimeProfile"));
    assertTrue(((List<?>) javascript.getDetails().get("globals")).size() > 0);
    assertTrue(((List<?>) javascript.getDetails().get("javaImports")).contains("java.util.UUID"));
  }

  private Optional<Metadata> findMetadata(List<Metadata> metadataList, String metadataName) {
    return metadataList.stream().filter(metadata -> metadataName.equals(metadata.getName())).findFirst();
  }

  private static class StubJavaTypeMetadataIndex implements JavaTypeMetadataIndex {
    private final List<String> importCandidates;
    private final List<ScriptIntelligenceJavaType> types;

    private StubJavaTypeMetadataIndex(List<String> importCandidates) {
      this.importCandidates = importCandidates;
      this.types = importCandidates.stream()
          .map(this::toType)
          .collect(Collectors.toList());
    }

    @Override
    public List<String> getImportCandidates() {
      return importCandidates;
    }

    @Override
    public List<ScriptIntelligenceJavaType> getTypes() {
      return types;
    }

    @Override
    public Optional<ScriptIntelligenceJavaType> findBySimpleName(String simpleName) {
      return types.stream().filter(type -> simpleName.equals(type.getSimpleName())).findFirst();
    }

    @Override
    public Optional<ScriptIntelligenceJavaType> findByQualifiedName(String qualifiedName) {
      return types.stream().filter(type -> qualifiedName.equals(type.getQualifiedName())).findFirst();
    }

    private ScriptIntelligenceJavaType toType(String importCandidate) {
      var javaType = new ScriptIntelligenceJavaType();
      javaType.setQualifiedName(importCandidate);
      javaType.setSimpleName(importCandidate.substring(importCandidate.lastIndexOf('.') + 1));
      return javaType;
    }
  }

  private static class FakeDates {
  }
}
