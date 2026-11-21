package com.ouroboros.vcs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.ouroboros.vcs.lock.LocalFileLockManager;

class GitBranchManagementIntegrationTest {

  private LocalGitVCSManager vcsManager;
  private Path repositoryDir;

  @BeforeEach
  void setUp() throws IOException {
    repositoryDir = Files.createTempDirectory("vcs-branch-repo-");
    VCSConfig config = new VCSConfig(repositoryDir.toString());
    config.setDefaultBranch("main");
    vcsManager = new LocalGitVCSManager(config, new LocalFileLockManager(repositoryDir + ".lock"));

    assertTrue(vcsManager.createFile("seed.txt")
        .content("seed")
        .commit("initial commit")
        .getOrElse(false));
  }

  @AfterEach
  void tearDown() throws IOException {
    if (vcsManager != null) {
      vcsManager.destroy();
    }
    deleteIfExists(repositoryDir);
    deleteIfExists(Paths.get(repositoryDir + ".lock"));
  }

  @Test
  void shouldManageBranchesAndWorkspaceCleanliness() {
    var current = vcsManager.getCurrentBranch();
    assertTrue(current.isSuccess());
    assertEquals("main", current.get());

    var branchCreated = vcsManager.createBranch("feature/remote-phase-a");
    assertTrue(branchCreated.isSuccess());

    var branches = vcsManager.getBranchList();
    assertTrue(branches.isSuccess());
    assertTrue(branches.get().contains("feature/remote-phase-a"));

    var switched = vcsManager.switchBranch("feature/remote-phase-a");
    assertTrue(switched.isSuccess());
    assertTrue(switched.get());
    assertEquals("feature/remote-phase-a", vcsManager.getCurrentBranch().get());

    var cleanBefore = vcsManager.isWorkspaceClean();
    assertTrue(cleanBefore.isSuccess());
    assertTrue(cleanBefore.get());

    var createAction = vcsManager.createFile("dirty.txt").content("dirty");
    var writeResult = createAction.write();
    assertTrue(writeResult.isSuccess());
    assertTrue(writeResult.get());

    var cleanAfterWrite = vcsManager.isWorkspaceClean();
    assertTrue(cleanAfterWrite.isSuccess());
    assertFalse(cleanAfterWrite.get());

    var commitResult = createAction.commit("commit dirty file");
    assertTrue(commitResult.isSuccess());
    assertTrue(commitResult.get());

    var cleanAfterCommit = vcsManager.isWorkspaceClean();
    assertTrue(cleanAfterCommit.isSuccess());
    assertTrue(cleanAfterCommit.get());
  }

  @Test
  void shouldInvalidateHeadContentCacheAfterBranchSwitch() {
    assertTrue(vcsManager.createBranch("feature/cache-invalidation").isSuccess());
    assertTrue(vcsManager.switchBranch("feature/cache-invalidation").getOrElse(false));
    assertTrue(vcsManager.updateFile("seed.txt").content("feature-content").commit("update on feature").getOrElse(false));

    assertEquals("feature-content", vcsManager.getFile("seed.txt", "HEAD").getContent());

    assertTrue(vcsManager.switchBranch("main").getOrElse(false));

    assertEquals("seed", vcsManager.getFile("seed.txt", "HEAD").getContent());
  }

  private void deleteIfExists(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return;
    }
    Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
  }
}
