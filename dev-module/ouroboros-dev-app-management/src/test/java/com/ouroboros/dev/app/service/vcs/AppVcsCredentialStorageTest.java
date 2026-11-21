package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.vcs.VCSConfig;

class AppVcsCredentialStorageTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldPersistCredentialsBindingsAndKeysUnderDevelopSupportInsteadOfAppRepository() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Path appRepo = Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppVcsLocalBindingStore store = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));

    AppVcsLocalBinding bindings = new AppVcsLocalBinding();
    bindings.setBindings(java.util.Arrays.asList(
        httpBinding("origin", "deploy-user", "deploy-password"),
        sshBinding("upstream", "deploy.key", "-----BEGIN PRIVATE KEY-----\nkey-material\n-----END PRIVATE KEY-----")
    ));
    assertTrue(store.save("crm", bindings).isSuccess());

    Path credentialsPath = store.credentialsPath("crm");
    Path bindingsPath = store.bindingsPath("crm");
    assertTrue(Files.exists(credentialsPath));
    assertTrue(Files.exists(bindingsPath));
    assertTrue(credentialsPath.startsWith(developSupport.resolve("vcs").resolve("crm")));
    assertTrue(bindingsPath.startsWith(developSupport.resolve("vcs").resolve("crm")));
    assertFalse(Files.exists(appRepo.resolve("credentials.json")));
    assertFalse(Files.exists(appRepo.resolve("bindings.json")));

    Path keyPath = store.keyPath("crm", "upstream", "deploy.key");
    assertTrue(Files.exists(keyPath));
    assertTrue(keyPath.startsWith(developSupport.resolve("vcs").resolve("crm").resolve("keys")));
    assertFalse(Files.exists(appRepo.resolve("keys").resolve("upstream").resolve("deploy.key")));

    Assumptions.assumeTrue(Files.getFileAttributeView(credentialsPath, PosixFileAttributeView.class) != null);
    assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(credentialsPath));
    assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(bindingsPath));
    assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(keyPath));
    assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(keyPath.getParent()));
  }

  @Test
  void shouldDeleteStaleSshKeysWhenRemoteKeyNameRotates() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-rotate"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppVcsLocalBindingStore store = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));

    AppVcsLocalBinding initial = new AppVcsLocalBinding();
    initial.setBindings(java.util.Collections.singletonList(
        sshBinding("origin", "old.key", "old-secret")
    ));
    assertTrue(store.save("crm", initial).isSuccess());

    Path oldKey = store.keyPath("crm", "origin", "old.key");
    assertTrue(Files.exists(oldKey));

    AppVcsLocalBinding rotated = new AppVcsLocalBinding();
    rotated.setBindings(java.util.Collections.singletonList(
        sshBinding("origin", "new.key", "new-secret")
    ));
    assertTrue(store.save("crm", rotated).isSuccess());

    Path newKey = store.keyPath("crm", "origin", "new.key");
    assertFalse(Files.exists(oldKey));
    assertTrue(Files.exists(newKey));
  }

  @Test
  void shouldPreserveExistingSshKeyWhenRotationValidationFails() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-rotate-fail"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppVcsLocalBindingStore store = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));

    AppVcsLocalBinding initial = new AppVcsLocalBinding();
    initial.setBindings(java.util.Collections.singletonList(
        sshBinding("origin", "old.key", "old-secret")
    ));
    assertTrue(store.save("crm", initial).isSuccess());

    Path oldKey = store.keyPath("crm", "origin", "old.key");
    assertTrue(Files.exists(oldKey));

    AppVcsRemoteBinding invalidRotation = new AppVcsRemoteBinding();
    invalidRotation.setRemoteName("origin");
    invalidRotation.setAuthType(VCSConfig.AuthType.SSH_KEY);
    invalidRotation.setSshKeyName("new.key");

    AppVcsLocalBinding rotated = new AppVcsLocalBinding();
    rotated.setBindings(java.util.Collections.singletonList(invalidRotation));

    Try<Void> result = store.save("crm", rotated);

    assertTrue(result.isFailure());
    assertTrue(Files.exists(oldKey));
    assertFalse(Files.exists(store.keyPath("crm", "origin", "new.key")));
  }

  @Test
  void shouldRejectUnsafeSshKeyPathOutsideDevelopSupport() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-safe"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppVcsLocalBindingStore store = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));
    AppVcsLocalBinding sshBinding = new AppVcsLocalBinding();
    sshBinding.setBindings(java.util.Collections.singletonList(
        sshBinding("origin", "../escape.key", "secret")
    ));

    Try<Void> result = store.save("crm", sshBinding);

    assertTrue(result.isFailure());
    assertFalse(Files.exists(developSupport.resolve("vcs").resolve("escape.key")));
    assertFalse(Files.exists(workspaceRoot.resolve("escape.key")));
  }

  @Test
  void shouldRejectReusingExistingSshKeyWhenKeyPathIsSymlink() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-symlink"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppVcsLocalBindingStore store = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));

    AppVcsLocalBinding initial = new AppVcsLocalBinding();
    initial.setBindings(java.util.Collections.singletonList(
        sshBinding("origin", "deploy.key", "trusted-key")
    ));
    assertTrue(store.save("crm", initial).isSuccess());

    Path keyPath = store.keyPath("crm", "origin", "deploy.key");
    Path externalKey = Files.write(tempDir.resolve("external.key"), "external-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Files.delete(keyPath);
    Files.createSymbolicLink(keyPath, externalKey);

    AppVcsRemoteBinding reuseWithoutPrivateKey = new AppVcsRemoteBinding();
    reuseWithoutPrivateKey.setRemoteName("origin");
    reuseWithoutPrivateKey.setAuthType(VCSConfig.AuthType.SSH_KEY);
    reuseWithoutPrivateKey.setSshKeyName("deploy.key");
    AppVcsLocalBinding update = new AppVcsLocalBinding();
    update.setBindings(java.util.Collections.singletonList(reuseWithoutPrivateKey));

    Try<Void> result = store.save("crm", update);

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("符号链接"));
  }

  @Test
  void shouldRejectLoadingExistingSshKeyWhenKeyPathIsSymlink() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-load-symlink"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppVcsLocalBindingStore store = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));

    AppVcsLocalBinding initial = new AppVcsLocalBinding();
    initial.setBindings(java.util.Collections.singletonList(
        sshBinding("origin", "deploy.key", "trusted-key")
    ));
    assertTrue(store.save("crm", initial).isSuccess());

    Path keyPath = store.keyPath("crm", "origin", "deploy.key");
    Path externalKey = Files.write(tempDir.resolve("external-load.key"), "external-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Files.delete(keyPath);
    Files.createSymbolicLink(keyPath, externalKey);

    Try<AppVcsLocalBinding> result = Try.of(() -> store.load("crm"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("符号链接"));
  }

  private static AppVcsRemoteBinding httpBinding(String remoteName, String username, String password) {
    AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
    binding.setRemoteName(remoteName);
    binding.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
    binding.setUsername(username);
    binding.setPassword(password);
    return binding;
  }

  private static AppVcsRemoteBinding sshBinding(String remoteName, String keyName, String privateKey) {
    AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
    binding.setRemoteName(remoteName);
    binding.setAuthType(VCSConfig.AuthType.SSH_KEY);
    binding.setSshKeyName(keyName);
    binding.setSshPrivateKey(privateKey);
    return binding;
  }

  private static DevelopWorkspaceContext workspaceContext(Path rootDir, Path devRoot, Path developSupport) {
    final Path runRoot = rootDir.resolve("run");
    final Path debugSupport = rootDir.resolve("debug-support");
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return rootDir.toString();
      }

      @Override
      public String getDevRootDir() {
        return devRoot.toString();
      }

      @Override
      public String getRunRootDir() {
        return runRoot.toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupport.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return developSupport.toString();
      }
    };
  }
}
