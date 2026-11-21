package com.ouroboros.vcs.versioning.git;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.jupiter.api.Test;

class JGitClientSyncResultSemanticsTest {

  @Test
  void emptyResourceDirectoriesAreCleanButUntrackedFilesStillBlockPublication() throws Exception {
    Path repositoryDir = Files.createTempDirectory("vcs-resource-clean-");
    var manager = new GitManager(repositoryDir.toString(), "main");
    try {
      try (var client = new JGitClient(repositoryDir.toString(), "main")) {
        assertTrue(client.isWorkspaceClean().get());
      }
      Files.createDirectory(repositoryDir.resolve("logic-flow"));
      assertTrue(manager.isWorkspaceClean().get());
      Path resource = repositoryDir.resolve("logic-flow/approve.json");
      Files.writeString(resource, "flow");
      assertFalse(manager.isWorkspaceClean().get());
      Files.delete(resource);
      assertTrue(manager.isWorkspaceClean().get());
    } finally {
      manager.destroy();
      deleteIfExists(repositoryDir);
    }
  }

  @Test
  void shouldFailWhenPullResultIsNotSuccessful() throws Exception {
    PullResult pullResult = pullResult(MergeResult.MergeStatus.CONFLICTING);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> JGitClient.validatePullResult(pullResult));

    assertTrue(exception.getMessage().toLowerCase().contains("conflict"));
  }

  @Test
  void shouldFailWhenPushResultContainsRejectedUpdate() throws Exception {
    Path repositoryDir = Files.createTempDirectory("vcs-push-result-");
    try (Git git = Git.init().setDirectory(repositoryDir.toFile()).setInitialBranch("main").call()) {
      RemoteRefUpdate update = new RemoteRefUpdate(
          git.getRepository(),
          "refs/heads/main",
          ObjectId.zeroId(),
          "refs/heads/main",
          false,
          null,
          null);
      setRemoteRefUpdateStatus(update, RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);

      PushResult pushResult = new PushResult();
      setPushResultUpdates(pushResult, Collections.singletonMap(update.getRemoteName(), update));

      IllegalStateException exception = assertThrows(IllegalStateException.class,
          () -> JGitClient.validatePushResults(Collections.singleton(pushResult), "main"));

      assertTrue(exception.getMessage().contains("REJECTED_NONFASTFORWARD"));
    } finally {
      deleteIfExists(repositoryDir);
    }
  }

  private static PullResult pullResult(MergeResult.MergeStatus status) throws Exception {
    MergeResult mergeResult = new MergeResult(
        null,
        null,
        new ObjectId[0],
        status,
        null,
        Collections.emptyMap());
    Constructor<PullResult> constructor = PullResult.class.getDeclaredConstructor(FetchResult.class, String.class, MergeResult.class);
    constructor.setAccessible(true);
    return constructor.newInstance(null, "origin", mergeResult);
  }

  private static void setRemoteRefUpdateStatus(RemoteRefUpdate update, RemoteRefUpdate.Status status) throws Exception {
    Method setStatus = RemoteRefUpdate.class.getDeclaredMethod("setStatus", RemoteRefUpdate.Status.class);
    setStatus.setAccessible(true);
    setStatus.invoke(update, status);
  }

  private static void setPushResultUpdates(PushResult pushResult, Map<String, RemoteRefUpdate> updates) throws Exception {
    Method setRemoteUpdates = PushResult.class.getDeclaredMethod("setRemoteUpdates", Map.class);
    setRemoteUpdates.setAccessible(true);
    setRemoteUpdates.invoke(pushResult, updates);
  }

  private static void deleteIfExists(Path path) throws Exception {
    if (path == null || !Files.exists(path)) {
      return;
    }
    Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
  }
}
