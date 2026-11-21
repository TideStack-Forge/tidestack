package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteDefinition;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

class VCSManagerCenterRefreshTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldUseUpdatedResolvedConfigAfterRefresh() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppHomeResolver appHomeResolver = appName -> devRoot.resolve(appName).toString();
    AppVcsPolicyStore policyStore = new AppVcsPolicyStore(appHomeResolver);
    AppVcsLocalBindingStore localBindingStore = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));
    AppVcsConfigResolver resolver = new AppVcsConfigResolver(appHomeResolver, policyStore, localBindingStore);
    VCSManagerCenter managerCenter = new VCSManagerCenter();
    ReflectionTestUtils.setField(managerCenter, "appHomeResolver", appHomeResolver);
    ReflectionTestUtils.setField(managerCenter, "vcsConfigResolver", resolver);

    policyStore.save("crm", policy("origin", "main", "https://example.com/repo-a.git")).get();
    localBindingStore.save("crm", httpBinding("origin", "user-a", "password-a")).get();

    VCSManager initialManager = managerCenter.getVcsManager("crm");
    VCSConfig initialConfig = extractConfig(initialManager);
    assertEquals("https://example.com/repo-a.git", initialConfig.getRemoteUri());
    assertEquals("user-a", initialConfig.getUsername());

    policyStore.save("crm", policy("upstream", "release", "https://example.com/repo-b.git")).get();
    localBindingStore.save("crm", httpBinding("upstream", "user-b", "password-b")).get();

    VCSManager cachedManager = managerCenter.getVcsManager("crm");
    assertSame(initialManager, cachedManager);
    assertEquals("https://example.com/repo-a.git", extractConfig(cachedManager).getRemoteUri());

    VCSManager refreshedManager = managerCenter.refresh("crm");
    VCSConfig refreshedConfig = extractConfig(refreshedManager);

    assertNotSame(initialManager, refreshedManager);
    assertEquals("https://example.com/repo-b.git", refreshedConfig.getRemoteUri());
    assertEquals("master", refreshedConfig.getDefaultBranch());
    assertEquals("user-b", refreshedConfig.getUsername());
    assertEquals("password-b", refreshedConfig.getPassword());
  }

  @Test
  void shouldCreateNewManagerAfterEvict() throws Exception {
    Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-evict"));
    Path devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
    Files.createDirectories(devRoot.resolve("crm"));
    Path developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
    AppHomeResolver appHomeResolver = appName -> devRoot.resolve(appName).toString();
    AppVcsConfigResolver resolver = new AppVcsConfigResolver(
        appHomeResolver,
        new AppVcsPolicyStore(appHomeResolver),
        new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport))
    );
    VCSManagerCenter managerCenter = new VCSManagerCenter();
    ReflectionTestUtils.setField(managerCenter, "appHomeResolver", appHomeResolver);
    ReflectionTestUtils.setField(managerCenter, "vcsConfigResolver", resolver);

    VCSManager first = managerCenter.getVcsManager("crm");
    managerCenter.evict("crm");
    VCSManager second = managerCenter.getVcsManager("crm");

    assertNotSame(first, second);
  }

  private static AppVcsPolicy policy(String defaultRemote, String remoteDefaultBranch, String remoteUri) {
    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setDefaultRemote(defaultRemote);
    AppVcsRemoteDefinition remote = new AppVcsRemoteDefinition();
    remote.setName(defaultRemote);
    remote.setUrl(remoteUri);
    remote.setDefaultBranch(remoteDefaultBranch);
    policy.setRemotes(java.util.Collections.singletonList(remote));
    return policy;
  }

  private static AppVcsLocalBinding httpBinding(String remoteName, String username, String password) {
    AppVcsLocalBinding binding = new AppVcsLocalBinding();
    AppVcsRemoteBinding remoteBinding = new AppVcsRemoteBinding();
    remoteBinding.setRemoteName(remoteName);
    remoteBinding.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
    remoteBinding.setUsername(username);
    remoteBinding.setPassword(password);
    binding.setBindings(java.util.Collections.singletonList(remoteBinding));
    return binding;
  }

  private static VCSConfig extractConfig(VCSManager manager) {
    return (VCSConfig) ReflectionTestUtils.getField(manager, "config");
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
