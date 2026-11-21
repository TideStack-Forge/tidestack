package com.ouroboros.logicflow.dev.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.logicflow.dev.vo.LogicFlowModelReadEnvelope;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodePositionMeta;
import com.ouroboros.logicflow.model.FlowModelValidationState;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileOperation;
import com.ouroboros.vcs.file.FileRevision;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

public class LogicModelDevelopServiceImplValidationTest {
  @Test
  public void publicationDependencyValidationDoesNotRequireCreatingOrOverwritingTheExistingFlow() {
    DevAppHolder.setAppName("logicflow-publication-dependency");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);
      var original = validFlow("existing", false);
      vcsManager.write("logic-flow/existing.logicflow.json", original);
      String before = vcsManager.read("logic-flow/existing.logicflow.json");
      var normalized = service.validateForPublication(original).get();
      assertFalse(normalized.isEnabled());
      assertNull(normalized.getValidationState());
      assertEquals(2, normalized.getNodes().size());
      assertEquals(before, vcsManager.read("logic-flow/existing.logicflow.json"));
      assertTrue(service.validateForPublication(invalidFlow("invalid", true)).isFailure());
      assertFalse(vcsManager.getFile("logic-flow/invalid.logicflow.json").exists());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void preparedResourcesUseFormalNormalizationWithoutWritingOrDroppingDisabledState() {
    DevAppHolder.setAppName("logicflow-prepared-resource");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);
      var result = service.prepareCreate(validFlow("sales.Order.approve", false));
      assertTrue(result.isSuccess());
      var prepared = result.get();
      assertEquals("logic-flow/sales.Order.approve.logicflow.json", prepared.getPrimaryPath());
      assertFalse(vcsManager.getFile(prepared.getPrimaryPath()).exists());
      var model = Json.toBean(FlowModelMeta.class, prepared.getPrimaryContent());
      assertFalse(model.isEnabled());
      assertEquals(2, model.getNodes().size());
      assertNotNull(model.getNodePositions().get("scriptNode"));
      assertNull(model.getValidationState());
      assertFalse(prepared.getPrimaryContent().contains("\"validationDiagnostics\""));
      var batch = vcsManager.batchOperation();
      prepared.appendTo(batch);
      assertTrue(batch.write(false).get());
      assertEquals(prepared.getPrimaryContent(), vcsManager.read(prepared.getPrimaryPath()));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void preparedResourcesRejectInvalidFlowsAndExistingLegacyResourcesBeforeSaving() {
    DevAppHolder.setAppName("logicflow-prepared-resource-conflict");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);
      assertTrue(service.prepareCreate(invalidFlow("invalid", true)).isFailure());
      assertFalse(vcsManager.getFile("logic-flow/invalid.logicflow.json").exists());
      vcsManager.write("logic-flow/occupied.json", validFlow("occupied", true));
      assertTrue(service.prepareCreate(validFlow("occupied", true)).isFailure());
      assertFalse(vcsManager.getFile("logic-flow/occupied.logicflow.json").exists());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void validateModelShouldOnlyRejectBlankName() {
    var service = new TestLogicModelDevelopServiceImpl(new FakeVCSManager());

    assertTrue(service.validateForTest(FlowModelMeta.builder().name(" ").build()).isFailure());
    assertTrue(service.validateForTest(invalidFlow("invalid-flow", true)).isSuccess());
  }

  @Test
  public void constructorShouldNotEnumerateMetadataProviders() {
    new LogicModelDevelopServiceImpl(new ObjectProvider<MetadataProvider>() {
      @Override
      public MetadataProvider getObject(Object... args) {
        throw new AssertionError("metadata provider should not be resolved during construction");
      }

      @Override
      public MetadataProvider getIfAvailable() {
        throw new AssertionError("metadata provider should not be resolved during construction");
      }

      @Override
      public MetadataProvider getIfUnique() {
        throw new AssertionError("metadata provider should not be resolved during construction");
      }

      @Override
      public MetadataProvider getObject() {
        throw new AssertionError("metadata provider should not be resolved during construction");
      }

      @Override
      public java.util.Iterator<MetadataProvider> iterator() {
        throw new AssertionError("metadata providers should not be enumerated during construction");
      }
    });
  }

  @Test
  public void createAndUpdateShouldPersistFormalModelWhileEnvelopeCarriesValidationSnapshot() {
    DevAppHolder.setAppName("logicflow-validation-test");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);

      var createResult = service.create(invalidFlow("flow-a", true), "", true);
      assertTrue(createResult.isSuccess());

      var created = (LogicFlowModelReadEnvelope) createResult.get();
      assertTrue(created.getFlowModel().isEnabled());
      assertNull(created.getFlowModel().getValidationState());
      assertTrue(created.getFlowModel().getValidationDiagnostics().isEmpty());
      assertNotNull(created.getFlowModel().getNodePositions().get("scriptNode"));
      assertEquals(Double.valueOf(620.0), created.getFlowModel().getNodePositions().get("scriptNode").getX());
      assertEquals(Double.valueOf(240.0), created.getFlowModel().getNodePositions().get("scriptNode").getY());
      assertEquals(FlowModelValidationState.INVALID, created.getValidationSnapshot().getValidationState());
      assertFalse(created.getValidationSnapshot().getValidationDiagnostics().isEmpty());

      var persistedCreated = Json.tryToBean(FlowModelMeta.class, vcsManager.read("logic-flow/flow-a.logicflow.json")).get();
      assertTrue(persistedCreated.isEnabled());
      assertNull(persistedCreated.getValidationState());
      assertTrue(persistedCreated.getValidationDiagnostics().isEmpty());
      assertNotNull(persistedCreated.getNodePositions().get("scriptNode"));
      assertEquals(Double.valueOf(620.0), persistedCreated.getNodePositions().get("scriptNode").getX());
      assertEquals(Double.valueOf(240.0), persistedCreated.getNodePositions().get("scriptNode").getY());
      assertTrue(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"nodePositions\""));
      assertFalse(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"validationState\""));
      assertFalse(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"validationSummary\""));
      assertFalse(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"validationErrors\""));
      assertFalse(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"validationDiagnostics\""));
      assertFalse(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"lastValidatedTime\""));

      var updateResult = service.update("flow-a", validFlow("flow-a", true), true);
      assertTrue(updateResult.isSuccess());

      var updated = (LogicFlowModelReadEnvelope) updateResult.get();
      assertTrue(updated.getFlowModel().isEnabled());
      assertNull(updated.getFlowModel().getValidationState());
      assertTrue(updated.getFlowModel().getValidationDiagnostics().isEmpty());
      assertNotNull(updated.getFlowModel().getNodePositions().get("scriptNode"));
      assertEquals(Double.valueOf(700.0), updated.getFlowModel().getNodePositions().get("scriptNode").getX());
      assertEquals(Double.valueOf(300.0), updated.getFlowModel().getNodePositions().get("scriptNode").getY());
      assertEquals(FlowModelValidationState.VALID, updated.getValidationSnapshot().getValidationState());
      assertTrue(updated.getValidationSnapshot().getValidationDiagnostics().isEmpty());

      var persistedUpdated = Json.tryToBean(FlowModelMeta.class, vcsManager.read("logic-flow/flow-a.logicflow.json")).get();
      assertNull(persistedUpdated.getValidationState());
      assertTrue(persistedUpdated.getValidationDiagnostics().isEmpty());
      assertNotNull(persistedUpdated.getNodePositions().get("scriptNode"));
      assertEquals(Double.valueOf(700.0), persistedUpdated.getNodePositions().get("scriptNode").getX());
      assertEquals(Double.valueOf(300.0), persistedUpdated.getNodePositions().get("scriptNode").getY());
      assertFalse(vcsManager.read("logic-flow/flow-a.logicflow.json").contains("\"validationState\""));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void getAndInitFormDataShouldReturnEnvelopeWhenValidationSnapshotWasRequested() {
    DevAppHolder.setAppName("logicflow-validation-test-envelope-read");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);

      var createResult = service.create(invalidFlow("flow-read", true), "", true);
      assertTrue(createResult.isSuccess());

      var getOneResult = service.getOne("flow-read", true);
      assertTrue(getOneResult.isSuccess());
      var getOneEnvelope = (LogicFlowModelReadEnvelope) getOneResult.get();
      assertNotNull(getOneEnvelope);
      assertNull(getOneEnvelope.getFlowModel().getValidationState());
      assertEquals(FlowModelValidationState.INVALID, getOneEnvelope.getValidationSnapshot().getValidationState());
      assertFalse(getOneEnvelope.getValidationSnapshot().getValidationDiagnostics().isEmpty());

      var initFormResult = service.getInitFormData("flow-read", true);
      assertTrue(initFormResult.isSuccess());
      var initFormEnvelope = (LogicFlowModelReadEnvelope) initFormResult.get();
      assertNull(initFormEnvelope.getFlowModel().getValidationState());
      assertEquals(FlowModelValidationState.INVALID, initFormEnvelope.getValidationSnapshot().getValidationState());
      assertFalse(initFormEnvelope.getValidationSnapshot().getValidationDiagnostics().isEmpty());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void searchWithValidationSnapshotShouldPreferTypedMetadataWhenLegacyHasSameName() {
    DevAppHolder.setAppName("logicflow-validation-search-dedupe");
    try {
      var vcsManager = new FakeVCSManager();
      vcsManager.write("logic-flow/flow-search.json", validFlow("flow-search", true, "legacy"));
      vcsManager.write("logic-flow/flow-search.logicflow.json", validFlow("flow-search", true, "typed"));
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);

      var results = service.search(Collections.emptyMap(), true);

      assertEquals(1, results.size());
      var result = (ModelFileMetaWrapper<?>) results.get(0);
      var envelope = (LogicFlowModelReadEnvelope) result.getModel();
      assertEquals("logic-flow/flow-search.logicflow.json", result.getFile().getPath());
      assertEquals("typed", envelope.getFlowModel().getDescription());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void createShouldPreserveDisabledStateWhenInvalidModelWasSubmittedDisabled() {
    DevAppHolder.setAppName("logicflow-validation-test-disabled");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestLogicModelDevelopServiceImpl(vcsManager);

      var createResult = service.create(invalidFlow("flow-b", false), "", true);
      assertTrue(createResult.isSuccess());

      var created = (LogicFlowModelReadEnvelope) createResult.get();
      assertFalse(created.getFlowModel().isEnabled());
      assertEquals(FlowModelValidationState.INVALID, created.getValidationSnapshot().getValidationState());
      assertFalse(created.getValidationSnapshot().getValidationDiagnostics().isEmpty());
      assertFalse(vcsManager.read("logic-flow/flow-b.logicflow.json").contains("\"validationState\""));
      assertFalse(vcsManager.read("logic-flow/flow-b.logicflow.json").contains("\"validationDiagnostics\""));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  public void currentApplicationSnapshotShouldResolveFlowAndDataModelReferences() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of(
        "logic-flow", List.of(metadata("logic-flow", "ChildFlow", validFlow("ChildFlow", true))),
        "data-model", List.of(metadata("data-model", "Customer", dataModel("Customer")))
    ));
    var provider = snapshotProvider(Map.of("reference-app", snapshot));

    var callFlowEnvelope = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(provider)),
        "reference-app",
        callFlow("caller-flow", "childflow")
    );
    var dataModelEnvelope = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(provider)),
        "reference-app",
        dataModelFlow("data-flow", "customer")
    );

    assertEquals(FlowModelValidationState.VALID, callFlowEnvelope.getValidationSnapshot().getValidationState());
    assertEquals(FlowModelValidationState.VALID, dataModelEnvelope.getValidationSnapshot().getValidationState());
  }

  @Test
  public void unreferencedMalformedMetadataShouldNotPreventMissingReferenceDiagnosis() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of(
        "logic-flow", List.of(metadata("logic-flow", "BrokenFlow", Collections.emptyMap())),
        "data-model", List.of(metadata("data-model", "BrokenModel", Collections.emptyMap()))
    ));
    var provider = snapshotProvider(Map.of("lazy-reference-app", snapshot));

    var callFlowEnvelope = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(provider)),
        "lazy-reference-app",
        callFlow("caller-flow", "missing-child")
    );
    var dataModelEnvelope = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(provider)),
        "lazy-reference-app",
        dataModelFlow("data-flow", "missing-model")
    );

    assertTrue(validationMessages(callFlowEnvelope).stream()
        .anyMatch(message -> message.contains("流程调用节点引用的流程不存在或未启用")));
    assertTrue(validationMessages(dataModelEnvelope).stream()
        .anyMatch(message -> message.contains("未找到数据模型：missing-model")));
  }

  @Test
  public void applicationMetadataSnapshotShouldDeeplyProtectNestedDetails() {
    var details = new LinkedHashMap<String, Object>();
    details.put("nested", new java.util.ArrayList<>(List.of("value")));
    var metadata = new Metadata();
    metadata.setName("Customer");
    metadata.setType("data-model");
    metadata.setDetails(details);
    var snapshot = ApplicationMetadataSnapshot.current(Map.of("data-model", List.of(metadata)));

    var nested = (List<?>) snapshot.getModule("data-model").get(0).getDetails().get("nested");

    assertThrows(UnsupportedOperationException.class, nested::clear);
  }

  @Test
  public void currentDraftWithDifferentCaseFromSnapshotShouldStillExposeDiagnostics() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of(
        "logic-flow", List.of(metadata("logic-flow", "ChildFlow", validFlow("ChildFlow", true)))
    ));
    var envelope = validate(
        new TestLogicModelDevelopServiceImpl(
            new FakeVCSManager(),
            List.of(snapshotProvider(Map.of("case-app", snapshot)))
        ),
        "case-app",
        callFlow("childflow", "missing-child")
    );

    assertTrue(validationMessages(envelope).stream()
        .anyMatch(message -> message.contains("流程不存在或未启用")));
  }

  @Test
  public void completeAndStaleSnapshotsShouldDistinguishMissingFromIndeterminate() {
    var currentProvider = snapshotProvider(Map.of(
        "current-app",
        ApplicationMetadataSnapshot.current(Collections.emptyMap())
    ));
    var staleProvider = snapshotProvider(Map.of(
        "stale-app",
        ApplicationMetadataSnapshot.stale(Collections.emptyMap())
    ));

    var missing = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(currentProvider)),
        "current-app",
        callFlow("missing-caller", "missing-child")
    );
    var indeterminate = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(staleProvider)),
        "stale-app",
        callFlow("unknown-caller", "unknown-child")
    );

    assertTrue(validationMessages(missing).stream().anyMatch(message -> message.contains("流程不存在或未启用")));
    assertTrue(validationMessages(indeterminate).stream()
        .anyMatch(message -> message.contains("流程引用目录不可用（INCOMPLETE）")));
  }

  @Test
  public void unavailableAndInvalidSnapshotsShouldExposeSpecificIndeterminateReasons() {
    var malformedFlow = new Metadata();
    malformedFlow.setType("logic-flow");
    malformedFlow.setName(null);
    malformedFlow.setDetails(Collections.emptyMap());
    var invalidProvider = snapshotProvider(Map.of(
        "invalid-app",
        ApplicationMetadataSnapshot.current(Map.of("logic-flow", List.of(malformedFlow)))
    ));

    var unavailable = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager()),
        "unavailable-app",
        callFlow("unavailable-caller", "unknown-child")
    );
    var invalid = validate(
        new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(invalidProvider)),
        "invalid-app",
        callFlow("invalid-caller", "unknown-child")
    );

    assertTrue(validationMessages(unavailable).stream()
        .anyMatch(message -> message.contains("流程引用目录不可用（NOT_READY）")));
    assertTrue(validationMessages(invalid).stream()
        .anyMatch(message -> message.contains("流程引用目录不可用（INVALID）")));
  }

  @Test
  public void caseInsensitiveDuplicateFlowReferencesInCurrentSnapshotShouldBeIndeterminate() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of(
        "logic-flow", List.of(
            metadata("logic-flow", "ChildFlow", validFlow("ChildFlow", true)),
            metadata("logic-flow", "childflow", validFlow("childflow", true))
        )
    ));

    var envelope = validate(
        new TestLogicModelDevelopServiceImpl(
            new FakeVCSManager(),
            List.of(snapshotProvider(Map.of("duplicate-flow-app", snapshot)))
        ),
        "duplicate-flow-app",
        callFlow("caller-flow", "missing-child")
    );

    assertTrue(validationMessages(envelope).stream()
        .anyMatch(message -> message.contains("流程引用目录不可用（INVALID）")));
  }

  @Test
  public void caseInsensitiveDuplicateDataModelReferencesInCurrentSnapshotShouldBeIndeterminate() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of(
        "data-model", List.of(
            metadata("data-model", "Customer", dataModel("Customer")),
            metadata("data-model", "customer", dataModel("customer"))
        )
    ));

    var envelope = validate(
        new TestLogicModelDevelopServiceImpl(
            new FakeVCSManager(),
            List.of(snapshotProvider(Map.of("duplicate-data-model-app", snapshot)))
        ),
        "duplicate-data-model-app",
        dataModelFlow("data-caller", "missing-model")
    );

    assertTrue(validationMessages(envelope).stream()
        .anyMatch(message -> message.contains("数据模型引用目录不可用（INVALID）")));
  }

  @Test
  public void nullMetadataInCurrentSnapshotShouldProduceInvalidDiagnostics() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of(
        "logic-flow", Arrays.asList((Metadata) null)
    ));

    var envelope = validate(
        new TestLogicModelDevelopServiceImpl(
            new FakeVCSManager(),
            List.of(snapshotProvider(Map.of("null-metadata-app", snapshot)))
        ),
        "null-metadata-app",
        callFlow("caller-flow", "missing-child")
    );

    assertTrue(validationMessages(envelope).stream()
        .anyMatch(message -> message.contains("流程引用目录不可用（INVALID）")));
  }

  @Test
  public void nullSnapshotModuleValueShouldProduceInvalidDiagnostics() {
    var modules = new LinkedHashMap<String, List<Metadata>>();
    modules.put("logic-flow", null);
    var snapshot = ApplicationMetadataSnapshot.current(modules);

    var envelope = validate(
        new TestLogicModelDevelopServiceImpl(
            new FakeVCSManager(),
            List.of(snapshotProvider(Map.of("null-module-app", snapshot)))
        ),
        "null-module-app",
        callFlow("caller-flow", "missing-child")
    );

    assertTrue(validationMessages(envelope).stream()
        .anyMatch(message -> message.contains("流程引用目录不可用（INVALID）")));
  }

  @Test
  public void workspaceFlowShouldRemainTrustedWhenApplicationSnapshotIsStale() {
    var staleProvider = snapshotProvider(Map.of(
        "workspace-app",
        ApplicationMetadataSnapshot.stale(Collections.emptyMap())
    ));
    MetadataProvider workspaceProvider = (appName, moduleName) -> {
      if (!"workspace-app".equals(appName) || !"logic-flow".equals(moduleName)) {
        return Collections.emptyList();
      }
      return List.of(metadata("logic-flow", "workspace-child", validFlow("workspace-child", true)));
    };

    var envelope = validate(
        new TestLogicModelDevelopServiceImpl(
            new FakeVCSManager(),
            List.of(staleProvider, workspaceProvider)
        ),
        "workspace-app",
        callFlow("workspace-caller", "workspace-child")
    );

    assertEquals(FlowModelValidationState.VALID, envelope.getValidationSnapshot().getValidationState());
  }

  @Test
  public void concurrentApplicationValidationShouldNotCrossReadFlowReferences() throws Exception {
    var barrier = new CyclicBarrier(2);
    var snapshots = Map.of(
        "app-a",
        ApplicationMetadataSnapshot.current(Map.of(
            "logic-flow",
            List.of(metadata("logic-flow", "child-a", validFlow("child-a", true)))
        )),
        "app-b",
        ApplicationMetadataSnapshot.current(Map.of(
            "logic-flow",
            List.of(metadata("logic-flow", "child-b", validFlow("child-b", true)))
        ))
    );
    var provider = new ApplicationMetadataSnapshotProvider() {
      @Override
      public ApplicationMetadataSnapshot getApplicationMetadataSnapshot(String appName) {
        try {
          barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        return snapshots.getOrDefault(appName, ApplicationMetadataSnapshot.unavailable());
      }
    };
    var executor = Executors.newFixedThreadPool(2);
    try {
      var appA = executor.submit(() -> validate(
          new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(provider)),
          "app-a",
          callFlow("caller-a", "child-a")
      ));
      var appB = executor.submit(() -> validate(
          new TestLogicModelDevelopServiceImpl(new FakeVCSManager(), List.of(provider)),
          "app-b",
          callFlow("caller-b", "child-b")
      ));

      assertEquals(FlowModelValidationState.VALID,
          appA.get(10, TimeUnit.SECONDS).getValidationSnapshot().getValidationState());
      assertEquals(FlowModelValidationState.VALID,
          appB.get(10, TimeUnit.SECONDS).getValidationSnapshot().getValidationState());
    } finally {
      executor.shutdownNow();
    }
  }

  private static FlowModelMeta validFlow(String name, boolean enabled) {
    return validFlow(name, enabled, null);
  }

  private static FlowModelMeta validFlow(String name, boolean enabled, String description) {
    return FlowModelMeta.builder()
        .name(name)
        .description(description)
        .entryNode("scriptNode")
        .enabled(enabled)
        .nodePositions(nodePositions(700.0, 300.0))
        .node(scriptNode("scriptNode"))
        .node(returnNode("returnNode"))
        .connect("scriptNode", "returnNode")
        .build();
  }

  private static FlowModelMeta invalidFlow(String name, boolean enabled) {
    return FlowModelMeta.builder()
        .name(name)
        .entryNode("scriptNode")
        .enabled(enabled)
        .nodePositions(nodePositions(620.0, 240.0))
        .node(scriptNode("scriptNode"))
        .build();
  }

  private static FlowModelMeta callFlow(String name, String targetFlowName) {
    return FlowModelMeta.builder()
        .name(name)
        .entryNode("caller")
        .enabled(true)
        .node(FlowNodeMeta.builder()
            .name("caller")
            .type("callFlow")
            .extraProp("flowName", targetFlowName)
            .build())
        .node(returnNode("returnNode"))
        .connect("caller", "returnNode")
        .build();
  }

  private static FlowModelMeta dataModelFlow(String name, String dataModelName) {
    return FlowModelMeta.builder()
        .name(name)
        .entryNode("model")
        .enabled(true)
        .node(FlowNodeMeta.builder()
            .name("model")
            .type("dataModel")
            .extraProp("dataModel", dataModelName)
            .build())
        .node(returnNode("returnNode"))
        .connect("model", "returnNode")
        .build();
  }

  private static Map<String, Object> dataModel(String name) {
    return Map.of(
        "name", name,
        "fields", List.of(Map.of("name", "id", "type", "String")),
        "primaryKeys", List.of("id")
    );
  }

  private static Metadata metadata(String moduleName, String name, Object details) {
    var metadata = new Metadata();
    metadata.setType(moduleName);
    metadata.setName(name);
    metadata.setDetails(Json.toMap(Json.toJsonString(details)));
    return metadata;
  }

  private static ApplicationMetadataSnapshotProvider snapshotProvider(
      Map<String, ApplicationMetadataSnapshot> snapshots
  ) {
    return appName -> snapshots.getOrDefault(appName, ApplicationMetadataSnapshot.unavailable());
  }

  private static LogicFlowModelReadEnvelope validate(
      TestLogicModelDevelopServiceImpl service,
      String appName,
      FlowModelMeta model
  ) {
    DevAppHolder.setAppName(appName);
    try {
      return (LogicFlowModelReadEnvelope) service.create(model, "", true).get();
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  private static List<String> validationMessages(LogicFlowModelReadEnvelope envelope) {
    return envelope.getValidationSnapshot().getValidationDiagnostics().stream()
        .map(diagnostic -> diagnostic.getMessage())
        .collect(java.util.stream.Collectors.toList());
  }

  private static Map<String, FlowNodePositionMeta> nodePositions(double x, double y) {
    var nodePositions = new LinkedHashMap<String, FlowNodePositionMeta>();
    nodePositions.put("scriptNode", FlowNodePositionMeta.builder().x(x).y(y).build());
    return nodePositions;
  }

  private static FlowNodeMeta scriptNode(String name) {
    return FlowNodeMeta.builder()
        .name(name)
        .type("script")
        .build();
  }

  private static FlowNodeMeta returnNode(String name) {
    return FlowNodeMeta.builder()
        .name(name)
        .type("return")
        .build();
  }

  private static class TestLogicModelDevelopServiceImpl extends LogicModelDevelopServiceImpl {
    private TestLogicModelDevelopServiceImpl(FakeVCSManager vcsManager) {
      this(vcsManager, Collections.emptyList());
    }

    private TestLogicModelDevelopServiceImpl(
        FakeVCSManager vcsManager,
        List<MetadataProvider> metadataProviders
    ) {
      super(new ListMetadataProviderObjectProvider(metadataProviders));
      this.vcsManagerCenter = new VCSManagerCenter() {
        @Override
        public VCSManager getVcsManager(String appName) {
          return vcsManager;
        }
      };
      this.publisher = new ApplicationEventPublisher() {
        @Override
        public void publishEvent(Object event) {
        }
      };
    }

    private Try<Void> validateForTest(FlowModelMeta model) {
      return super.validateModel(model);
    }
  }

  private static class ListMetadataProviderObjectProvider implements ObjectProvider<MetadataProvider> {
    private final List<MetadataProvider> providers;

    private ListMetadataProviderObjectProvider(List<MetadataProvider> providers) {
      this.providers = providers;
    }

    @Override
    public MetadataProvider getObject(Object... args) {
      return getObject();
    }

    @Override
    public MetadataProvider getIfAvailable() {
      return providers.isEmpty() ? null : providers.get(0);
    }

    @Override
    public MetadataProvider getIfUnique() {
      return providers.size() == 1 ? providers.get(0) : null;
    }

    @Override
    public MetadataProvider getObject() {
      if (providers.isEmpty()) {
        throw new IllegalStateException("No metadata provider available");
      }
      return providers.get(0);
    }

    @Override
    public java.util.Iterator<MetadataProvider> iterator() {
      return providers.iterator();
    }

    @Override
    public Stream<MetadataProvider> stream() {
      return providers.stream();
    }
  }

  private static class FakeVCSManager implements VCSManager {
    private final Map<String, String> files = new HashMap<>();

    private String read(String path) {
      return files.get(path);
    }

    private void write(String path, Object model) {
      files.put(path, Json.toPrettyJsonString(model));
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return new FakeVCSFile(relativePath, files);
    }

    @Override
    public Try<Void> lock(String relativePath, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> lock(Collection<String> relativePaths, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(String relativePath, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(Collection<String> relativePaths, boolean force) {
      return Try.success(null);
    }

    @Override
    public VCSAction.CreateFileAction createFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.RemoveFileAction removeFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.UpdateFileAction updateFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.MoveFileAction moveFile(String relativePath, String newRelativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction batchOperation() {
      return new FakeBatchAction(files);
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      String prefix = relativeParentPath.endsWith("/") ? relativeParentPath : relativeParentPath + "/";
      return files.keySet().stream()
          .filter(path -> path.startsWith(prefix) && path.endsWith(fileExtension))
          .sorted(String::compareToIgnoreCase)
          .map(path -> new FakeVCSFile(path, files));
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getUncommittedFiles(String relativePath) {
      return Collections.emptyList();
    }

    @Override
    public VCSFile getFile(String relativePath, String revision) {
      return getFile(relativePath);
    }

    @Override
    public VCSFile getFileFromBranch(String relativePath, String branchName) {
      return getFile(relativePath);
    }

    @Override
    public VCSFile getFileFromTag(String relativePath, String tagName) {
      return getFile(relativePath);
    }

    @Override
    public List<VCSFile> getFileList(String relativeParentPath, String revision) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName) {
      return Collections.emptyList();
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension) {
      return Stream.empty();
    }

    @Override
    public Try<String> createTag(String tagName, String message) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<List<Tag>> getTagList() {
      return Try.success(Collections.emptyList());
    }

    @Override
    public Try<String> getCurrentBranch() {
      return Try.success("main");
    }

    @Override
    public Try<List<String>> getBranchList() {
      return Try.success(Collections.singletonList("main"));
    }

    @Override
    public Try<String> createBranch(String branchName) {
      return Try.success(branchName);
    }

    @Override
    public Try<String> createBranch(String branchName, String fromRevision) {
      return Try.success(branchName);
    }

    @Override
    public Try<Boolean> switchBranch(String branchName) {
      return Try.success(true);
    }

    @Override
    public Try<VCSConfig.RemoteConfig> getRemoteConfig() {
      return Try.success(null);
    }

    @Override
    public Try<Boolean> setRemoteConfig(VCSConfig.RemoteConfig remoteConfig) {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> pull() {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> push() {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> isWorkspaceClean() {
      return Try.success(true);
    }
  }

  private static class FakeBatchAction implements VCSAction.BatchAction {
    private final Map<String, String> files;
    private final Map<String, String> creates = new HashMap<>();
    private final Map<String, String> updates = new HashMap<>();
    private final List<Runnable> validations = new ArrayList<>();

    @Override
    public VCSAction.BatchAction beforeWrite(Runnable validation) {
      validations.add(validation);
      return this;
    }

    private FakeBatchAction(Map<String, String> files) {
      this.files = files;
    }

    @Override
    public VCSAction.BatchAction committer(String user) {
      return this;
    }

    @Override
    public VCSAction.BatchAction committerEmail(String email) {
      return this;
    }

    @Override
    public VCSAction.BatchAction autoLock() {
      return this;
    }

    @Override
    public VCSAction.BatchAction unlockWhenCommit() {
      return this;
    }

    @Override
    public Try<Boolean> write(boolean forceWrite) {
      validations.forEach(Runnable::run);
      apply();
      return Try.success(true);
    }

    @Override
    public Try<Boolean> commit(String message) {
      validations.forEach(Runnable::run);
      apply();
      return Try.success(true);
    }

    @Override
    public VCSAction.BatchAction add(String relativePath) {
      return this;
    }

    @Override
    public VCSAction.BatchAction add(Collection<String> relativePaths) {
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, String content) {
      creates.put(relativePath, content);
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      return create(relativePath, new String(content));
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      files.remove(relativePath);
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(Collection<String> relativePaths) {
      relativePaths.forEach(files::remove);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      updates.put(relativePath, content);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      return update(relativePath, new String(content));
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath, java.util.function.Function<String, String> updateContent) {
      throw new UnsupportedOperationException();
    }

    private void apply() {
      files.putAll(creates);
      files.putAll(updates);
      creates.clear();
      updates.clear();
    }
  }

  private static class FakeVCSFile implements VCSFile {
    private final String path;
    private final Map<String, String> files;

    private FakeVCSFile(String path, Map<String, String> files) {
      this.path = path;
      this.files = files;
    }

    @Override
    public boolean exists() {
      return files.containsKey(path);
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public String getFileName() {
      return path;
    }

    @Override
    public String getContent() {
      return files.get(path);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(getBytesArray());
    }

    @Override
    public byte[] getBytesArray() {
      return getContent() == null ? new byte[0] : getContent().getBytes();
    }

    @Override
    public Long getSize() {
      return (long) getBytesArray().length;
    }

    @Override
    public LocalDateTime getCreationTime() {
      return null;
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      return null;
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      return null;
    }

    @Override
    public VersionState getVersionState() {
      return VersionState.UNKNOWN;
    }

    @Override
    public LockState getLockState() {
      return LockState.UNLOCKED;
    }

    @Override
    public String getLockOwner() {
      return null;
    }

    @Override
    public String getLockOwnerName() {
      return null;
    }

    @Override
    public Try<Void> lock(boolean forceLock) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(boolean forceUnlock) {
      return Try.success(null);
    }

    @Override
    public Try<Boolean> write(String content) {
      files.put(path, content);
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      files.put(path, new String(content));
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<VCSFile> remove() {
      files.remove(path);
      return Try.success(this);
    }

    @Override
    public Try<Boolean> commit(String message) {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> rollback(boolean forceRollback) {
      return Try.success(true);
    }

    @Override
    public String getRevision() {
      return null;
    }

    @Override
    public boolean isHistorical() {
      return false;
    }

    @Override
    public VCSFile atRevision(String revision) {
      return this;
    }

    @Override
    public VCSFile atBranch(String branchName) {
      return this;
    }

    @Override
    public VCSFile atTag(String tagName) {
      return this;
    }

    @Override
    public Try<FileDiff> diffWith(String otherRevision) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public List<FileRevision> getRevisions() {
      return Collections.emptyList();
    }

    @Override
    public List<FileRevision> getRevisions(int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public boolean isOperationAllowed(FileOperation operation) {
      return VCSFile.super.isOperationAllowed(operation);
    }
  }
}
