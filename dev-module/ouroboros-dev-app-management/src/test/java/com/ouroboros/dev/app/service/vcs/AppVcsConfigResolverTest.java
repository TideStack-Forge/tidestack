package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteDefinition;
import com.ouroboros.vcs.VCSConfig;

class AppVcsConfigResolverTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldResolveDefaultRemoteAndItsBindingFromMultiRemotePolicy() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Path appHome = Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppHomeResolver appHomeResolver = appName -> devRoot.resolve(appName).toString();
    AppVcsPolicyStore policyStore = new AppVcsPolicyStore(appHomeResolver);
    AppVcsLocalBindingStore localBindingStore = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));
    AppVcsConfigResolver resolver = new AppVcsConfigResolver(appHomeResolver, policyStore, localBindingStore);

    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote("upstream");
    policy.setRemotes(Arrays.asList(
        remote("origin", "https://example.com/ouroboros/crm.git", "main"),
        remote("upstream", "ssh://git@example.com/team/crm.git", "release/main")
    ));
    policyStore.save("crm", policy).get();

    AppVcsLocalBinding binding = new AppVcsLocalBinding();
    binding.setBindings(Arrays.asList(
        httpBinding("origin", "origin-user", "origin-password"),
        sshBinding("upstream", "deploy-key", "private-key")
    ));
    localBindingStore.save("crm", binding).get();

    VCSConfig resolved = resolver.resolve("crm");

    assertEquals(appHome.toString(), resolved.getRepositoryPath());
    assertEquals("upstream", resolved.getRemoteName());
    assertEquals("ssh://git@example.com/team/crm.git", resolved.getRemoteUri());
    assertEquals("master", resolved.getDefaultBranch());
    assertEquals(VCSConfig.AuthType.SSH_KEY, resolved.getAuthType());
    assertEquals(Paths.get(developSupport.toString(), "vcs", "crm", "keys", "upstream", "deploy-key").toAbsolutePath().normalize().toString(), resolved.getSshKeyPath());
    assertEquals(Paths.get(appHome.toString(), AppVcsPolicyStore.POLICY_RELATIVE_PATH).toAbsolutePath().normalize(),
        appHome.resolve(Paths.get(".ouroboros", "vcs", "policy.json")).toAbsolutePath().normalize());
  }

  @Test
  void shouldNotFallbackToSingleBindingWhenDefaultRemoteHasNoMatchingCredentialBinding() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-mismatch"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppHomeResolver appHomeResolver = appName -> devRoot.resolve(appName).toString();
    AppVcsPolicyStore policyStore = new AppVcsPolicyStore(appHomeResolver);
    AppVcsLocalBindingStore localBindingStore = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));
    AppVcsConfigResolver resolver = new AppVcsConfigResolver(appHomeResolver, policyStore, localBindingStore);

    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote("upstream");
    policy.setRemotes(Arrays.asList(
        remote("origin", "https://example.com/ouroboros/crm.git", "main"),
        remote("upstream", "ssh://git@example.com/team/crm.git", "release/main")
    ));
    policyStore.save("crm", policy).get();

    AppVcsLocalBinding binding = new AppVcsLocalBinding();
    binding.setBindings(Arrays.asList(
        httpBinding("origin", "origin-user", "origin-password")
    ));
    localBindingStore.save("crm", binding).get();

    VCSConfig resolved = resolver.resolve("crm");

    assertEquals("upstream", resolved.getRemoteName());
    assertEquals("ssh://git@example.com/team/crm.git", resolved.getRemoteUri());
    assertEquals(VCSConfig.AuthType.NONE, resolved.getAuthType());
    assertNull(resolved.getUsername());
    assertNull(resolved.getPassword());
  }

  @Test
  void shouldRejectSshKeyBindingForHttpsRemoteWhenResolvingConfig() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-auth-mismatch"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppHomeResolver appHomeResolver = appName -> devRoot.resolve(appName).toString();
    AppVcsPolicyStore policyStore = new AppVcsPolicyStore(appHomeResolver);
    AppVcsLocalBindingStore localBindingStore = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));
    AppVcsConfigResolver resolver = new AppVcsConfigResolver(appHomeResolver, policyStore, localBindingStore);

    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote("origin");
    policy.setRemotes(Arrays.asList(
        remote("origin", "https://example.com/ouroboros/crm.git", "master")
    ));
    policyStore.save("crm", policy).get();

    AppVcsLocalBinding binding = new AppVcsLocalBinding();
    AppVcsRemoteBinding remoteBinding = new AppVcsRemoteBinding();
    remoteBinding.setRemoteName("origin");
    remoteBinding.setAuthType(VCSConfig.AuthType.SSH_KEY);
    remoteBinding.setSshKeyName("deploy-key");
    remoteBinding.setSshPrivateKey("private-key");
    binding.setBindings(Arrays.asList(remoteBinding));
    localBindingStore.save("crm", binding).get();

    assertThrows(RuntimeException.class, () -> resolver.resolve("crm"));
  }

  private static AppVcsRemoteDefinition remote(String name, String url, String defaultBranch) {
    AppVcsRemoteDefinition remote = new AppVcsRemoteDefinition();
    remote.setName(name);
    remote.setUrl(url);
    remote.setDefaultBranch(defaultBranch);
    return remote;
  }

  private static AppVcsRemoteBinding httpBinding(String remoteName, String username, String password) {
    AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
    binding.setRemoteName(remoteName);
    binding.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
    binding.setUsername(username);
    binding.setPassword(password);
    return binding;
  }

  private static AppVcsRemoteBinding sshBinding(String remoteName, String sshKeyName, String sshPrivateKey) {
    AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
    binding.setRemoteName(remoteName);
    binding.setAuthType(VCSConfig.AuthType.SSH_KEY);
    binding.setSshKeyName(sshKeyName);
    binding.setSshPrivateKey(sshPrivateKey);
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
