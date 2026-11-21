package com.ouroboros.vcs.versioning.git;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.ouroboros.vcs.VCSConfig;

class GitAuthStrategySelectionTest {

  private Path repositoryDir;
  private JGitClient client;

  @BeforeEach
  void setUp() throws Exception {
    repositoryDir = Files.createTempDirectory("vcs-auth-");
    try (Git ignored = Git.init().setDirectory(repositoryDir.toFile()).setInitialBranch("main").call()) {
      // init repository
    }
    client = new JGitClient(repositoryDir.toString(), "main");
  }

  @AfterEach
  void tearDown() throws IOException {
    if (client != null) {
      client.close();
    }
    deleteIfExists(repositoryDir);
  }

  @Test
  void shouldSelectHttpBasicCredentialsProvider() throws Exception {
    VCSConfig.AuthConfig authConfig = new VCSConfig.AuthConfig();
    authConfig.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
    authConfig.setUsername("demo-user");
    authConfig.setPassword("demo-password");

    try (Git git = Git.open(repositoryDir.toFile())) {
      PullCommand command = git.pull();
      client.applyAuthConfig(command, authConfig);

      assertNotNull(readField(command, "credentialsProvider"));
      assertNull(readField(command, "transportConfigCallback"));
    }
  }

  @Test
  void shouldSelectSshTransportConfigCallback() throws Exception {
    VCSConfig.AuthConfig authConfig = new VCSConfig.AuthConfig();
    authConfig.setAuthType(VCSConfig.AuthType.SSH_KEY);
    authConfig.setSshKeyPath("/tmp/not-used-by-test/id_rsa");

    try (Git git = Git.open(repositoryDir.toFile())) {
      PullCommand command = git.pull();
      client.applyAuthConfig(command, authConfig);

      assertNotNull(readField(command, "transportConfigCallback"));
      assertNull(readField(command, "credentialsProvider"));
    }
  }

  @Test
  void shouldSelectNoAuthWhenConfigAbsent() throws Exception {
    try (Git git = Git.open(repositoryDir.toFile())) {
      PullCommand command = git.pull();
      client.applyAuthConfig(command, null);

      assertNull(readField(command, "transportConfigCallback"));
      assertNull(readField(command, "credentialsProvider"));
    }
  }

  private Object readField(TransportCommand<?, ?> command, String fieldName) throws Exception {
    Field field = TransportCommand.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(command);
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
