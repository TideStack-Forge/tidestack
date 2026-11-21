package com.ouroboros.dev.app.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.collaboration.CollaborativeResource;
import com.ouroboros.dev.app.collaboration.CollaborativeDocumentService.*;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.action.DefaultBatchAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.filesystem.LocalFileSystemManager;
import com.ouroboros.vcs.lock.LocalFileLockManager;
import com.ouroboros.vcs.versioning.git.GitManager;

class CollaborativeDocumentServiceTest {
  @TempDir Path home;
  private VCSManager manager;
  private VCSManagerCenter center;
  private LocalFileSystemManager files;
  private LocalFileLockManager locks;
  private GitManager versions;
  private CollaborativeDocumentService service;
  private final CollaborativeResource resource = new CollaborativeResource() {
    public String collaborationType() { return "canvas"; }
    public String collaborationDirectory() { return "business-model/canvases"; }
    public String collaborationSuffix() { return ".canvas.json"; }
    public void validateCollaborationSeed(String id, String json) {
      if (json == null || !json.startsWith("{")) throw new ValidationException("seed");
    }
  };

  @BeforeEach
  void setUp() throws Exception {
    var root = Files.createDirectory(home.resolve("metadata"));
    files = new LocalFileSystemManager(root.toString());
    locks = new LocalFileLockManager(home.resolve("metadata.lock").toString());
    versions = new GitManager(root.toString(), "main");
    var user = new VCSUserProvider() {
      public String getCurrentUser() { return "alice"; }
      public String getCurrentUserEmail() { return "alice@example.test"; }
    };
    manager = mock(VCSManager.class);
    when(manager.getFile(anyString())).thenAnswer(call -> file(call.getArgument(0)));
    when(manager.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user));
    when(manager.walkDirectory(anyString(), anyString())).thenAnswer(call -> {
      var directory = root.resolve((String) call.getArgument(0));
      if (!Files.exists(directory)) return Stream.empty();
      try (var paths = Files.walk(directory)) {
        return paths.filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith((String) call.getArgument(1)))
            .map(path -> file(root.relativize(path).toString())).toList().stream();
      }
    });
    center = mock(VCSManagerCenter.class);
    when(center.getVcsManager("app")).thenReturn(manager);
    service = new CollaborativeDocumentService(center, List.of(resource));
  }

  private VCSFile file(String path) {
    var file = mock(VCSFile.class);
    when(file.exists()).thenAnswer(ignore -> files.exists(path));
    when(file.getContent()).thenAnswer(ignore -> files.getFileContent(path).get());
    when(file.getFileName()).thenReturn(Path.of(path).getFileName().toString());
    return file;
  }

  private Document initialize(String id) {
    return service.initialize("app", "canvas", id, new Initialization("{\"id\":\"" + id + "\"}", "AAE="), "alice");
  }

  @Test
  void migratesLegacyFileOnceAndRestoresTheCheckpointAndConcurrentUpdatesFromVcs() {
    var path = "business-model/canvases/overview.canvas.json";
    files.createFile(path, "{\"id\":\"overview\"}").get();
    assertNull(service.list("app", "canvas").getFirst().checkpoint());
    var initial = initialize("overview");
    assertEquals(initial, service.initialize("app", "canvas", "overview",
        new Initialization("{\"id\":\"overview\"}", "AgM="), "bob"));
    service.apply("app", "canvas", "overview", new Operation("a", 1, "AgM=", "AgM="), "alice");
    var combined = service.apply("app", "canvas", "overview", new Operation("b", 1, "BAU=", "BAU="), "bob");
    assertEquals(2, combined.updates().size());
    assertEquals("AgM=", combined.checkpoint());
    assertEquals(combined, new CollaborativeDocumentService(center, List.of(resource)).get("app", "canvas", "overview"));
    assertNull(locks.getLockOwner(path));
    assertTrue(versions.isWorkspaceClean().get());
  }

  @Test
  void concurrentWritersBothSucceedAndStaleCheckpointsCannotEraseEachOthersOperations() throws Exception {
    initialize("overview");
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var attempts = List.of("alice", "bob").stream().map(user -> executor.submit(() -> {
        start.await();
        return service.apply("app", "canvas", "overview", new Operation(user, 1, "AgM=", "BAU="), user);
      })).toList();
      start.countDown();
      for (var attempt : attempts) assertNotNull(attempt.get());
    }
    var current = service.get("app", "canvas", "overview");
    assertEquals(3, current.revision());
    assertEquals(2, current.updates().size());
    var compacted = service.apply("app", "canvas", "overview", new Operation("c", 3, "Bgc=", "CAk="), "alice");
    assertEquals("CAk=", compacted.checkpoint());
    assertEquals(1, compacted.updates().size());
    assertEquals(compacted, service.apply("app", "canvas", "overview", new Operation("c", 3, "Bgc=", "CAk="), "alice"));
    assertThrows(ValidationException.class, () -> service.apply("app", "canvas", "overview", new Operation("c", 3, "BAU=", "CAk="), "alice"));
  }

  @Test
  void validatesNamespaceIdsEncodingAndRevisionBeforeWritingAndKeepsApplicationsSeparate() {
    assertThrows(ValidationException.class, () -> initialize("../outside"));
    assertThrows(ValidationException.class, () -> service.list("app", "unknown"));
    initialize("overview");
    assertThrows(ValidationException.class, () -> service.apply("app", "canvas", "overview", new Operation("bad", 1, "!", "AAE="), "alice"));
    assertThrows(ValidationException.class, () -> service.apply("app", "canvas", "overview", new Operation("future", 99, "AAE=", "AAE="), "alice"));
    assertEquals(1, service.get("app", "canvas", "overview").revision());
    var other = mock(VCSManager.class);
    when(other.walkDirectory(anyString(), anyString())).thenReturn(Stream.empty());
    when(center.getVcsManager("other")).thenReturn(other);
    assertTrue(service.list("other", "canvas").isEmpty());
  }
}
