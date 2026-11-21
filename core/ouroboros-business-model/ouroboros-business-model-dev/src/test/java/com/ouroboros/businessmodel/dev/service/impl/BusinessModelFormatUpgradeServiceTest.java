package com.ouroboros.businessmodel.dev.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.dev.vo.BusinessModelReadEnvelope;
import com.ouroboros.businessmodel.dev.controller.BusinessModelController;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.action.DefaultBatchAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.filesystem.LocalFileSystemManager;
import com.ouroboros.vcs.lock.LocalFileLockManager;
import com.ouroboros.vcs.versioning.git.GitManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BusinessModelFormatUpgradeServiceTest {
  private static final String NAME = "upgradePreview.Order";
  private static final String MODEL = "business-model/" + NAME + ".business.json";
  private static final String FLOW = "logic-flow/" + NAME + ".approve.logicflow.json";
  private static final String DRAFT = """
      {"modelType":"default","namespace":"upgradePreview","name":"Order","title":"订单",
       "future":{"keep":"原文"},"custom":[{"name":"approve","title":"确认",
       "api":{"isEnabled":true,"isGenerateImplementation":true,"scriptLanguage":"groovy",
       "script":"return request.params.id + ':' + request.body.note","url":"confirm/{id}","httpMethod":"POST",
       "actionInputMapping":{"params.id":"$.pathVariables.id","data":"$.body"}}}]}
      """;
  @TempDir Path home;
  private LocalFileSystemManager files;
  private LocalFileLockManager locks;
  private GitManager versions;
  private VCSManager vcs;
  private TestService service;
  private TestFlowService flows;
  private final List<Object> events = new ArrayList<>();
  private final VCSUserProvider user = new VCSUserProvider() {
    public String getCurrentUser() { return "modeler"; }
    public String getCurrentUserEmail() { return "modeler@example.test"; }
  };

  @BeforeEach
  void setUp() throws Exception {
    DevAppHolder.setAppName("format-upgrade-test");
    var repository = Files.createDirectory(home.resolve("metadata"));
    files = new LocalFileSystemManager(repository.toString());
    locks = new LocalFileLockManager(home.resolve("metadata.lock").toString());
    versions = new GitManager(repository.toString(), "main");
    files.createFile(MODEL, DRAFT).get();
    versions.startChain().user(user.getCurrentUser()).email(user.getCurrentUserEmail()).add(MODEL).commit("baseline").get();
    vcs = mock(VCSManager.class);
    when(vcs.getFile(anyString())).thenAnswer(invocation -> {
      String path = invocation.getArgument(0);
      var file = mock(VCSFile.class);
      when(file.getPath()).thenReturn(path);
      when(file.exists()).thenAnswer(ignore -> files.exists(path));
      when(file.getContent()).thenAnswer(ignore -> files.getFileContent(path).get());
      when(file.getBytesArray()).thenAnswer(ignore -> files.getFileBytes(path).get());
      return file;
    });
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user));
    service = new TestService(vcs);
    flows = new TestFlowService(vcs);
    service.logicModelDevService = flows;
  }

  @AfterEach
  void tearDown() {
    DevAppHolder.removeAppName();
    locks.destroy(); versions.destroy(); files.destroy();
  }

  @Test
  void previewIsReadOnlyAndApplicationCommitsTheWholePlanWithTheSameScriptAndAuthority() {
    var preview = service.previewFormatUpgrade(NAME, DRAFT.replace("订单", "订单草稿"), "preview-1").get();
    assertEquals("preview-1", preview.requestId());
    assertEquals(2, preview.resources().size());
    assertEquals("2.0", preview.upgradedModel().getFormatVersion());
    assertEquals(NAME + ".approve", preview.before().catalog().executionAuthorities().get("approve"));
    assertEquals(preview.before().catalog().executionAuthorities(), preview.after().catalog().executionAuthorities());
    assertEquals(DRAFT, read(MODEL));
    assertFalse(files.exists(FLOW));
    assertTrue(versions.isWorkspaceClean().get());
    assertTrue(events.isEmpty());
    assertNull(locks.getLockOwner(MODEL));
    var result = service.applyFormatUpgrade(NAME, DRAFT.replace("订单", "订单草稿"), preview.planFingerprint());
    assertTrue(result.isSuccess(), () -> String.valueOf(result.getCause()));
    assertInstanceOf(BusinessModelReadEnvelope.class, result.get());
    assertEquals("2.0", service.getOne(NAME).orElseThrow().getFormatVersion());
    assertEquals("订单草稿", service.getOne(NAME).orElseThrow().getTitle());
    assertEquals(Map.of("keep", "原文"), service.getOne(NAME).orElseThrow().getExtraProp("future").orElseThrow());
    assertTrue(read(FLOW).contains("request.params.id + ':' + request.body.note"));
    assertEquals(2, versions.getChangeLogs(MODEL).size());
    assertEquals(1, versions.getChangeLogs(FLOW).size());
    assertTrue(versions.isWorkspaceClean().get());
    assertEquals(1, events.size());
    assertNull(locks.getLockOwner(MODEL));
    assertNull(locks.getLockOwner(FLOW));
    assertTrue(service.previewFormatUpgrade(NAME, read(MODEL), "already-new").isFailure());
  }

  @Test
  void httpPreviewAndExplicitApplicationUseTheEstablishedEnvelopeAndCompleteDraft() throws Exception {
    var controller = new BusinessModelController();
    org.springframework.test.util.ReflectionTestUtils.setField(controller, "businessModelDevService", service);
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var previewResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/dev/example/business-model/" + NAME + "/format-upgrade-preview")
        .param("requestId", "http-upgrade-1").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(DRAFT))
        .andReturn().getResponse();
    assertEquals(200, previewResponse.getStatus());
    var preview = (Map<?, ?>) Json.toMap(previewResponse.getContentAsString(StandardCharsets.UTF_8)).get("data");
    assertEquals("http-upgrade-1", preview.get("requestId"));
    assertEquals(DRAFT, read(MODEL));
    var applied = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/dev/example/business-model/" + NAME + "/format-upgrade")
        .param("planFingerprint", preview.get("planFingerprint").toString())
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(DRAFT)).andReturn().getResponse();
    assertEquals(200, applied.getStatus());
    var envelope = (Map<?, ?>) Json.toMap(applied.getContentAsString(StandardCharsets.UTF_8)).get("data");
    assertEquals("2.0", ((Map<?, ?>) envelope.get("businessModel")).get("formatVersion"));
    assertTrue(files.exists(FLOW));
    assertEquals(1, events.size());
  }

  @Test
  void staleDraftOrStoredContentCannotApplyAnEarlierPreview() {
    var preview = service.previewFormatUpgrade(NAME, DRAFT, "original").get();
    assertTrue(service.applyFormatUpgrade(NAME, DRAFT.replace("订单", "changed draft"), preview.planFingerprint()).isFailure());
    assertTrue(service.applyFormatUpgrade(NAME, DRAFT, null).isFailure());
    files.write(MODEL, DRAFT + "\n").get();
    assertTrue(service.applyFormatUpgrade(NAME, DRAFT, preview.planFingerprint()).isFailure());
    assertEquals(DRAFT + "\n", read(MODEL));
    assertFalse(files.exists(FLOW));
    assertTrue(events.isEmpty());
  }

  @Test
  void changesBetweenRecomputationAndLockedWriteAreRejectedWithoutOverwritingThem() {
    var preview = service.previewFormatUpgrade(NAME, DRAFT, "original").get();
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user) {
      @Override protected io.vavr.control.Try<Void> doLock(String owner) {
        var locked = super.doLock(owner);
        files.write(MODEL, DRAFT + "\n").get();
        return locked;
      }
    });
    var result = service.applyFormatUpgrade(NAME, DRAFT, preview.planFingerprint());
    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("预览已过期"));
    assertEquals(DRAFT + "\n", read(MODEL));
    assertFalse(files.exists(FLOW));
    assertNull(locks.getLockOwner(MODEL));
    assertTrue(events.isEmpty());
  }

  @Test
  void commitRejectionRestoresOldBytesAndDoesNotPublishHalfTheResourceSet() throws Exception {
    var preview = service.previewFormatUpgrade(NAME, DRAFT, "preview").get();
    Path hook = home.resolve("metadata/.git/hooks/pre-commit");
    Files.writeString(hook, "#!/bin/sh\nexit 1\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(hook, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE));
    assertTrue(service.applyFormatUpgrade(NAME, DRAFT, preview.planFingerprint()).isFailure());
    assertEquals(DRAFT, read(MODEL));
    assertFalse(files.exists(FLOW));
    try (var git = org.eclipse.jgit.api.Git.open(home.resolve("metadata").toFile())) {
      var status = git.status().call();
      assertTrue(versions.isWorkspaceClean().get(), () -> "changed=" + status.getChanged() + "; modified="
          + status.getModified() + "; untracked=" + status.getUntracked() + "; removed=" + status.getRemoved()
          + "; added=" + status.getAdded() + "; missing=" + status.getMissing());
    }
    assertTrue(events.isEmpty());
    assertEquals(1, versions.getChangeLogs(MODEL).size());
  }

  @Test
  void resourceCollisionUnavailableFlowServiceAndInvalidPreparationNeverDropScripts() {
    files.createFile(FLOW, "existing flow").get();
    assertTrue(service.previewFormatUpgrade(NAME, DRAFT, "collision").isFailure());
    files.delete(FLOW).get();
    service.logicModelDevService = null;
    assertTrue(service.previewFormatUpgrade(NAME, DRAFT, "unavailable").isFailure());
    service.logicModelDevService = flows;
    flows.reject = true;
    assertTrue(service.previewFormatUpgrade(NAME, DRAFT, "invalid-flow").isFailure());
    assertEquals(DRAFT, read(MODEL));
    assertFalse(files.exists(FLOW));
    assertTrue(events.isEmpty());
  }

  @Test
  void existingFlowDependenciesAreValidatedAndTheirChangesInvalidateThePreview() {
    String custom = DRAFT.replace("\"scriptLanguage\":\"groovy\",", "\"isCustomLogicFlow\":true,")
        .replace("\"script\":\"return request.params.id + ':' + request.body.note\",", "");
    files.write(MODEL, custom).get();
    assertTrue(service.previewFormatUpgrade(NAME, custom, "missing-flow").isFailure());
    var flow = FlowModelMeta.builder().name(NAME + ".approve").enabled(true).build();
    files.createFile(FLOW, Json.toJsonString(flow)).get();
    flows.reject = true;
    assertTrue(service.previewFormatUpgrade(NAME, custom, "invalid-dependency").isFailure());
    flows.reject = false;
    flow = FlowModelMeta.builder().name(NAME + ".approve").enabled(false).build();
    files.write(FLOW, Json.toJsonString(flow)).get();
    assertTrue(service.previewFormatUpgrade(NAME, custom, "disabled-dependency").isFailure());
    flow = FlowModelMeta.builder().name(NAME + ".approve").enabled(true).build();
    files.write(FLOW, Json.toJsonString(flow)).get();
    var preview = service.previewFormatUpgrade(NAME, custom, "existing-flow").get();
    assertEquals(1, preview.resources().size());
    flow = FlowModelMeta.builder().name(NAME + ".approve").enabled(true).description("new dependency version").build();
    files.write(FLOW, Json.toJsonString(flow)).get();
    assertTrue(service.applyFormatUpgrade(NAME, custom, preview.planFingerprint()).isFailure());
    assertEquals(custom, read(MODEL));
    assertTrue(events.isEmpty());
  }

  @Test
  void identityVersionAndFormalBehaviorErrorsCannotBeConvertedToASuccessfulPreview() {
    assertTrue(service.previewFormatUpgrade("missing.Model", DRAFT, "missing").isFailure());
    assertTrue(service.previewFormatUpgrade(NAME, "{", "syntax").isFailure());
    assertTrue(service.previewFormatUpgrade(NAME, DRAFT.replace("\"Order\"", "\"Other\""), "identity").isFailure());
    assertTrue(service.previewFormatUpgrade(NAME, DRAFT.replace("\"modelType\":\"default\"",
        "\"modelType\":\"default\",\"formatVersion\":\"1.0\""), "version").isFailure());
    assertTrue(service.previewFormatUpgrade(NAME, DRAFT.replace("\"scriptLanguage\":\"groovy\"",
        "\"scriptLanguage\":\"groovy\",\"emits\":[{\"modelFullName\":\"upgradePreview.Order\",\"eventName\":\"unknownEvent\"}]"), "behavior").isFailure());
    String highCode = """
        {"modelType":"default","namespace":"upgradePreview","name":"Order","title":"订单",
         "custom":[{"name":"missingCodeContribution","api":{"isEnabled":true,"isBindAction":true,
         "actionInputMapping":{}}}]}
        """;
    assertTrue(service.previewFormatUpgrade(NAME, highCode, "code-not-loaded").isFailure());
    assertEquals(DRAFT, read(MODEL));
    assertTrue(versions.isWorkspaceClean().get());
    assertTrue(events.isEmpty());
  }

  private String read(String path) {
    return new String(files.getFileBytes(path).get(), StandardCharsets.UTF_8);
  }

  @Test
  void scriptActionPreviewIsReadOnlyAndCommitsOneFormalFlowWithTheUniqueActionAndReference() {
    var request = scriptRequest();
    String before = read(MODEL);
    var preview = service.previewScriptAction(NAME, request, "script-1").get();
    assertEquals("script-1", preview.requestId()); assertEquals(2, preview.resources().size());
    assertEquals(NAME + ".calculate", preview.flowName()); assertEquals("calculate", preview.actionName());
    assertEquals(before, read(MODEL)); assertFalse(files.exists(scriptFlow())); assertTrue(events.isEmpty());
    assertTrue(service.createScriptAction(NAME, request, preview.planFingerprint()).isSuccess());
    assertEquals(1, events.size()); assertTrue(versions.isWorkspaceClean().get()); assertNull(locks.getLockOwner(MODEL));
    var saved = (com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta) service.getOne(NAME).orElseThrow();
    assertEquals(2, saved.getActions().size());
    assertEquals("approve", saved.getActions().getFirst().getName());
    assertEquals("calculate", ((Map<?, ?>) ((Map<?, ?>) saved.getExtraProp(List.class, "custom").orElseThrow().getFirst()).get("api")).get("action"));
    var flow = flows.getOne(NAME + ".calculate").orElseThrow();
    var script = flow.getNodes().stream().filter(node -> "script".equals(node.getType())).findFirst().orElseThrow();
    assertEquals("input", script.getInputPorts().getFirst().getName()); assertEquals(request.script(), script.getExtraProps().get("script"));
    assertFalse(read(MODEL).contains(request.script()));
    assertTrue(service.previewScriptAction(NAME, request, "occupied").isFailure());
  }

  @Test
  void scriptActionRejectsStaleDraftDependencyAndLockedStoredContentBeforeAnyWrites() {
    var request = scriptRequest();
    var preview = service.previewScriptAction(NAME, request, "script-1").get();
    var changed = new com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest(request.businessModel(),
        request.functionName(), request.actionName(), request.scriptLanguage(), "return 'changed'", request.readOnly());
    assertTrue(service.createScriptAction(NAME, changed, preview.planFingerprint()).isFailure());
    assertTrue(service.createScriptAction(NAME, request, null).isFailure());
    String originalFlow = read(FLOW);
    files.write(FLOW, originalFlow.replace("脚本逻辑模型", "changed dependency")).get();
    assertTrue(service.createScriptAction(NAME, request, preview.planFingerprint()).isFailure());
    files.write(FLOW, originalFlow).get();
    String before = read(MODEL);
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user) {
      @Override protected Try<Void> doLock(String owner) {
        var result = super.doLock(owner);
        files.write(MODEL, before + "\n").get();
        return result;
      }
    });
    assertTrue(service.createScriptAction(NAME, request, preview.planFingerprint()).isFailure());
    assertEquals(before + "\n", read(MODEL)); assertFalse(files.exists(scriptFlow()));
    assertTrue(events.isEmpty()); assertNull(locks.getLockOwner(MODEL));
  }

  @Test
  void scriptActionCommitFailureRestoresBothResourcesAndKeepsTheOriginalAction() throws Exception {
    var request = scriptRequest(); var preview = service.previewScriptAction(NAME, request, "script-1").get();
    String before = read(MODEL);
    Path hook = home.resolve("metadata/.git/hooks/pre-commit");
    Files.writeString(hook, "#!/bin/sh\nexit 1\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(hook, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE));
    assertTrue(service.createScriptAction(NAME, request, preview.planFingerprint()).isFailure());
    assertEquals(before, read(MODEL)); assertFalse(files.exists(scriptFlow()));
    assertTrue(files.exists(FLOW)); assertTrue(events.isEmpty()); assertTrue(versions.isWorkspaceClean().get());
  }

  @Test
  void scriptActionCannotBypassVersionIdentityWholeModelValidationOrFormalFlowPreparation() {
    var legacy = new com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest(Json.toMap(DRAFT),
        "approve", "calculate", "groovy", "return $input.data", true);
    assertTrue(service.previewScriptAction(NAME, legacy, "legacy").isFailure());
    assertTrue(service.previewScriptAction(NAME, null, "missing").isFailure());
    var request = scriptRequest();
    assertTrue(service.previewScriptAction("missing.Order", request, "missing").isFailure());
    var model = new java.util.LinkedHashMap<>(request.businessModel());
    model.put("name", "Other");
    var foreign = new com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest(model,
        request.functionName(), request.actionName(), request.scriptLanguage(), request.script(), request.readOnly());
    assertTrue(service.previewScriptAction(NAME, foreign, "identity").isFailure());
    model = new java.util.LinkedHashMap<>(request.businessModel());
    model.put("actions", List.of(Map.of("name", "invalid", "isEnabled", true)));
    var invalid = new com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest(model,
        request.functionName(), request.actionName(), request.scriptLanguage(), request.script(), request.readOnly());
    assertTrue(service.previewScriptAction(NAME, invalid, "invalid").isFailure());
    flows.reject = true; assertTrue(service.previewScriptAction(NAME, request, "invalid-flow").isFailure());
    flows.reject = false;
    service.logicModelDevService = null; assertTrue(service.previewScriptAction(NAME, request, "unavailable").isFailure());
    assertFalse(files.exists(scriptFlow())); assertTrue(events.isEmpty()); assertTrue(versions.isWorkspaceClean().get());
  }

  @Test
  void scriptActionHttpEndpointsUseTheCompleteDraftAndTheExistingReadEnvelope() throws Exception {
    var request = scriptRequest();
    var controller = new BusinessModelController();
    org.springframework.test.util.ReflectionTestUtils.setField(controller, "businessModelDevService", service);
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/dev/example/business-model/" + NAME + "/script-action-preview")
        .param("requestId", "http-script-1").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .content(Json.toJsonString(request))).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    var preview = (Map<?, ?>) Json.toMap(response.getContentAsString(StandardCharsets.UTF_8)).get("data");
    assertEquals("http-script-1", preview.get("requestId")); assertFalse(files.exists(scriptFlow()));
    var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/dev/example/business-model/" + NAME + "/script-action")
        .param("planFingerprint", preview.get("planFingerprint").toString()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .content(Json.toJsonString(request))).andReturn().getResponse();
    assertEquals(200, created.getStatus());
    var envelope = (Map<?, ?>) Json.toMap(created.getContentAsString(StandardCharsets.UTF_8)).get("data");
    assertEquals("2.0", ((Map<?, ?>) envelope.get("businessModel")).get("formatVersion"));
    assertTrue(files.exists(scriptFlow())); assertEquals(1, events.size());
  }

  private com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest scriptRequest() {
    var upgrade = service.previewFormatUpgrade(NAME, DRAFT, "upgrade").get();
    service.applyFormatUpgrade(NAME, DRAFT, upgrade.planFingerprint()).get();
    events.clear();
    return new com.ouroboros.businessmodel.dev.vo.BusinessModelScriptActionRequest(Json.toMap(read(MODEL)),
        "approve", "calculate", "groovy", "return $input.params.id + ':' + $input.data.note", true);
  }

  private String scriptFlow() { return "logic-flow/" + NAME + ".calculate.logicflow.json"; }

  private class TestService extends BusinessModelDevService {
    TestService(VCSManager manager) {
      vcsManagerCenter = mock(VCSManagerCenter.class);
      when(vcsManagerCenter.getVcsManager(anyString())).thenReturn(manager);
      publisher = events::add;
    }
    @Override public List<BusinessModelMeta> getAll() { return getOne(NAME).stream().toList(); }
  }

  private static class TestFlowService extends BaseDevelopService<FlowModelMeta> {
    boolean reject;
    TestFlowService(VCSManager manager) {
      vcsManagerCenter = mock(VCSManagerCenter.class);
      when(vcsManagerCenter.getVcsManager(anyString())).thenReturn(manager);
    }
    @Override protected String getModelFileDirectory() { return "logic-flow"; }
    @Override protected String getModelDisplayName() { return "逻辑流"; }
    @Override protected String getModelFileExtension() { return ".logicflow.json"; }
    @Override protected Class<FlowModelMeta> getModelClass() { return FlowModelMeta.class; }
    @Override protected String getFullName(FlowModelMeta model) { return model.getName(); }
    @Override protected Try<Void> validateModel(FlowModelMeta model) {
      return reject ? Try.failure(new BusinessException("invalid associated flow")) : Try.success(null);
    }
    @Override public Try<FlowModelMeta> validateForPublication(FlowModelMeta model) {
      return validateModel(model).map(ignore -> model);
    }
  }
}
