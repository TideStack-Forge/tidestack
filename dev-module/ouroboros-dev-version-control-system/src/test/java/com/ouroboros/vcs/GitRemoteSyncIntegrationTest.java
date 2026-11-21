package com.ouroboros.vcs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.ouroboros.vcs.lock.LocalFileLockManager;

class GitRemoteSyncIntegrationTest {

  private LocalGitVCSManager vcsManager;
  private Path repositoryDir;
  private Path remoteBareRepoDir;
  private Path peerRepoDir;

  @BeforeEach
  void setUp() throws IOException {
    repositoryDir = Files.createTempDirectory("vcs-sync-local-");
    remoteBareRepoDir = Files.createTempDirectory("vcs-sync-remote-");
    peerRepoDir = Files.createTempDirectory("vcs-sync-peer-");

    VCSConfig config = new VCSConfig(repositoryDir.toString());
    config.setDefaultBranch("main");
    config.setRemoteName("origin");
    vcsManager = new LocalGitVCSManager(config, new LocalFileLockManager(repositoryDir + ".lock"));

    assertTrue(vcsManager.createFile("base.txt")
        .content("base")
        .commit("initial commit")
        .getOrElse(false));
  }

  @AfterEach
  void tearDown() throws IOException {
    if (vcsManager != null) {
      vcsManager.destroy();
    }
    deleteIfExists(repositoryDir);
    deleteIfExists(remoteBareRepoDir);
    deleteIfExists(peerRepoDir);
    deleteIfExists(Paths.get(repositoryDir + ".lock"));
  }

  @Test
  void shouldPushAndPullUsingBareRemoteRepository() throws Exception {
    try (Git ignored = Git.init()
        .setDirectory(remoteBareRepoDir.toFile())
        .setBare(true)
        .setInitialBranch("main")
        .call()) {
      // bare remote initialized
    }

    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri(remoteBareRepoDir.toUri().toString());
    remoteConfig.setRemoteBranch("main");

    var setRemoteResult = vcsManager.setRemoteConfig(remoteConfig);
    assertTrue(setRemoteResult.isSuccess());
    assertTrue(setRemoteResult.get());

    var pushResult = vcsManager.push();
    assertTrue(pushResult.isSuccess());
    assertTrue(pushResult.get());

    var savedRemoteConfig = vcsManager.getRemoteConfig();
    assertTrue(savedRemoteConfig.isSuccess());
    assertEquals("origin", savedRemoteConfig.get().getRemoteName());
    assertEquals(remoteBareRepoDir.toUri().toString(), savedRemoteConfig.get().getRemoteUri());
    assertEquals("main", savedRemoteConfig.get().getRemoteBranch());

    try (Git peerGit = Git.cloneRepository()
        .setURI(remoteBareRepoDir.toUri().toString())
        .setDirectory(peerRepoDir.toFile())
        .setBranch("main")
        .call()) {
      Files.write(peerRepoDir.resolve("from-peer.txt"), "peer-change".getBytes(StandardCharsets.UTF_8));
      peerGit.add().addFilepattern("from-peer.txt").call();
      peerGit.commit()
          .setAuthor("peer", "peer@example.com")
          .setCommitter("peer", "peer@example.com")
          .setMessage("peer commit")
          .call();
      peerGit.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main"))
          .call();
    }

    var pullResult = vcsManager.pull();
    assertTrue(pullResult.isSuccess());
    assertTrue(pullResult.get());

    var pulledFile = vcsManager.getFile("from-peer.txt");
    assertTrue(pulledFile.exists());
    assertEquals("peer-change", pulledFile.getContent());
  }

  @Test
  void shouldInvalidateHeadContentCacheAfterPull() throws Exception {
    initializeRemote();
    assertTrue(vcsManager.push().getOrElse(false));

    assertEquals("base", vcsManager.getFile("base.txt", "HEAD").getContent());

    try (Git peerGit = Git.cloneRepository()
        .setURI(remoteBareRepoDir.toUri().toString())
        .setDirectory(peerRepoDir.toFile())
        .setBranch("main")
        .call()) {
      Files.write(peerRepoDir.resolve("base.txt"), "peer-updated-base".getBytes(StandardCharsets.UTF_8));
      peerGit.add().addFilepattern("base.txt").call();
      peerGit.commit()
          .setAuthor("peer", "peer@example.com")
          .setCommitter("peer", "peer@example.com")
          .setMessage("update base")
          .call();
      peerGit.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main"))
          .call();
    }

    assertTrue(vcsManager.pull().getOrElse(false));
    assertEquals("peer-updated-base", vcsManager.getFile("base.txt", "HEAD").getContent());
  }

  @Test
  void shouldFailPushWhenRemoteRejectsNonFastForwardUpdate() throws Exception {
    initializeRemote();
    assertTrue(vcsManager.push().getOrElse(false));

    try (Git peerGit = Git.cloneRepository()
        .setURI(remoteBareRepoDir.toUri().toString())
        .setDirectory(peerRepoDir.toFile())
        .setBranch("main")
        .call()) {
      Files.write(peerRepoDir.resolve("peer.txt"), "peer-change".getBytes(StandardCharsets.UTF_8));
      peerGit.add().addFilepattern("peer.txt").call();
      peerGit.commit()
          .setAuthor("peer", "peer@example.com")
          .setCommitter("peer", "peer@example.com")
          .setMessage("peer commit")
          .call();
      peerGit.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main"))
          .call();
    }

    assertTrue(vcsManager.createFile("local.txt")
        .content("local-change")
        .commit("local commit")
        .getOrElse(false));

    var pushResult = vcsManager.push();

    assertTrue(pushResult.isFailure());
    assertTrue(pushResult.getCause().getMessage().contains("REJECTED_NONFASTFORWARD"));
  }

  private void initializeRemote() throws Exception {
    try (Git ignored = Git.init()
        .setDirectory(remoteBareRepoDir.toFile())
        .setBare(true)
        .setInitialBranch("main")
        .call()) {
      // bare remote initialized
    }

    VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
    remoteConfig.setRemoteName("origin");
    remoteConfig.setRemoteUri(remoteBareRepoDir.toUri().toString());
    remoteConfig.setRemoteBranch("main");
    assertTrue(vcsManager.setRemoteConfig(remoteConfig).getOrElse(false));
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
