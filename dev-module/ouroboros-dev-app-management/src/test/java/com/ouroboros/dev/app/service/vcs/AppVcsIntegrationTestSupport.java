package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppBranchSwitchRequest;
import com.ouroboros.dev.app.model.AppManagedBranchCreateRequest;
import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.service.impl.AppVersionServiceImpl;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

final class AppVcsIntegrationTestSupport {
  static final String APP_NAME = "crm";
  static final String TRUNK_BRANCH = "master";
  static final String CUSTOM_BRANCH = "custom/premium";
  static final String POLICY_FILE = AppVcsPolicyStore.POLICY_RELATIVE_PATH.replace('\\', '/');

  private AppVcsIntegrationTestSupport() {
  }

  static Scenario create(Path tempDir) throws Exception {
    return new Scenario(tempDir);
  }

  static AppManagedBranchCreateRequest customBranchRequest() {
    AppManagedBranchCreateRequest request = new AppManagedBranchCreateRequest();
    request.setEditionCode("premium");
    return request;
  }

  static AppBranchSwitchRequest switchBranchRequest(String branchName) {
    AppBranchSwitchRequest request = new AppBranchSwitchRequest();
    request.setBranchName(branchName);
    return request;
  }

  static final class Scenario implements AutoCloseable {
    final Path workspaceRoot;
    final Path devRoot;
    final Path appHome;
    final Path developSupport;
    final Path remoteBareRepo;
    final String remoteUri;
    final String transportRemoteUri;
    final AppVcsPolicyStore policyStore;
    final AppVcsLocalBindingStore localBindingStore;
    final AppVcsConfigService configService;
    final AppVcsFacade vcsFacade;
    final AppVersionServiceImpl versionService;

    private final VCSManagerCenter managerCenter;
    private final Process gitDaemonProcess;

    private Scenario(Path tempDir) throws Exception {
      workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
      devRoot = Files.createDirectories(workspaceRoot.resolve("dev"));
      appHome = Files.createDirectories(devRoot.resolve(APP_NAME));
      developSupport = Files.createDirectories(workspaceRoot.resolve("develop-support"));
      remoteBareRepo = Files.createDirectories(tempDir.resolve("remote-bare.git"));
      initBareRemote(remoteBareRepo);
      gitDaemonProcess = startGitDaemon(tempDir, remoteBareRepo);
      transportRemoteUri = buildRemoteUri(remoteBareRepo, gitDaemonPort(tempDir));
      remoteUri = "https://example.com/team/" + APP_NAME + ".git";

      AppHomeResolver appHomeResolver = appName -> devRoot.resolve(appName).toString();
      policyStore = new AppVcsPolicyStore(appHomeResolver);
      localBindingStore = new AppVcsLocalBindingStore(workspaceContext(workspaceRoot, devRoot, developSupport));
      AppVcsConfigResolver resolver = new AppVcsConfigResolver(appHomeResolver, policyStore, localBindingStore);

      managerCenter = new VCSManagerCenter();
      ReflectionTestUtils.setField(managerCenter, "appHomeResolver", appHomeResolver);
      ReflectionTestUtils.setField(managerCenter, "vcsConfigResolver", resolver);

      configService = new AppVcsConfigService(policyStore, localBindingStore, managerCenter);
      vcsFacade = new AppVcsFacade(managerCenter, policyStore);
      versionService = new AppVersionServiceImpl(managerCenter, vcsFacade);
    }

    void saveRemotePolicy() {
      AppVcsPolicy policy = new AppVcsPolicy();
      policy.setRemoteName("origin");
      policy.setRemoteUri(remoteUri);
      policy.setDefaultBranch(TRUNK_BRANCH);
      assertTrue(configService.savePolicy(APP_NAME, policy).isSuccess());
    }

    void initializeRepositoryWithTrackedPolicy() {
      assertTrue(vcsManager().createFile("README.md")
          .content("seed")
          .commit("initial commit")
          .getOrElse(false));
      assertTrue(vcsManager().getFile(POLICY_FILE).commit("track vcs policy").getOrElse(false));
    }

    void saveHttpBinding() {
      AppVcsLocalBinding binding = new AppVcsLocalBinding();
      binding.setRemoteName("origin");
      binding.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
      binding.setUsername("deploy-user");
      binding.setPassword("deploy-password");
      assertTrue(configService.saveLocalBinding(APP_NAME, binding).isSuccess());
    }

    void commitLocalFile(String relativePath, String content, String message) {
      assertTrue(vcsManager().createFile(relativePath)
          .content(content)
          .commit(message)
          .getOrElse(false));
    }

    void syncCurrentBranchViaTransport(boolean pull) {
      VCSManager manager = vcsManager();
      String currentBranch = manager.getCurrentBranch().get();
      VCSConfig.RemoteConfig remoteConfig = new VCSConfig.RemoteConfig();
      remoteConfig.setRemoteName("origin");
      remoteConfig.setRemoteUri(transportRemoteUri);
      remoteConfig.setRemoteBranch(currentBranch);
      assertTrue(manager.setRemoteConfig(remoteConfig).getOrElse(false));

      VCSConfig.AuthConfig authConfig = new VCSConfig.AuthConfig();
      authConfig.setAuthType(VCSConfig.AuthType.NONE);
      resolvedConfig().setAuthConfig(authConfig);

      if (pull) {
        assertTrue(manager.pull().getOrElse(false));
      } else {
        assertTrue(manager.push().getOrElse(false));
      }
      managerCenter.refresh(APP_NAME);
    }

    Path createSupportKeyFile(String keyName, String content) throws IOException {
      Path keyPath = localBindingStore.keyPath(APP_NAME, keyName);
      Path parent = keyPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(keyPath, content.getBytes(StandardCharsets.UTF_8));
      return keyPath;
    }

    void commitAndPushFromPeer(String branchName, String relativePath, String content, String message) throws Exception {
      Path cloneDir = Files.createDirectories(workspaceRoot.resolve("peer-" + sanitizeBranch(branchName)));
      try (Git peerGit = Git.cloneRepository()
          .setURI(transportRemoteUri)
          .setDirectory(cloneDir.toFile())
          .setBranch(branchName)
          .call()) {
        Path targetFile = cloneDir.resolve(relativePath);
        Path parent = targetFile.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.write(targetFile, content.getBytes(StandardCharsets.UTF_8));
        peerGit.add().addFilepattern(relativePath).call();
        peerGit.commit()
            .setAuthor("peer", "peer@example.com")
            .setCommitter("peer", "peer@example.com")
            .setMessage(message)
            .call();
        peerGit.push()
            .setRemote("origin")
            .setRefSpecs(new RefSpec("refs/heads/" + branchName + ":refs/heads/" + branchName))
            .call();
      }
    }

    VCSManager vcsManager() {
      return managerCenter.getVcsManager(APP_NAME);
    }

    VCSConfig resolvedConfig() {
      return (VCSConfig) ReflectionTestUtils.getField(vcsManager(), "config");
    }

    @Override
    public void close() {
      managerCenter.evict(APP_NAME);
      stopGitDaemon(gitDaemonProcess);
    }

    private static void initBareRemote(Path bareRepo) throws Exception {
      try (Git ignored = Git.init()
          .setDirectory(bareRepo.toFile())
          .setBare(true)
          .setInitialBranch(TRUNK_BRANCH)
          .call()) {
        // bare remote initialized
      }
    }

    private static Process startGitDaemon(Path tempDir, Path bareRepo) throws Exception {
      int port = reservePort();
      Path portFile = tempDir.resolve("git-daemon.port");
      Files.write(portFile, String.valueOf(port).getBytes(StandardCharsets.UTF_8));
      Path basePath = bareRepo.getParent();
      Path logFile = tempDir.resolve("git-daemon.log");
      Process process = new ProcessBuilder(
          "git",
          "daemon",
          "--reuseaddr",
          "--export-all",
          "--enable=receive-pack",
          "--base-path=" + basePath,
          "--listen=127.0.0.1",
          "--port=" + port,
          basePath.toString())
          .redirectErrorStream(true)
          .redirectOutput(logFile.toFile())
          .start();
      waitForDaemon(port);
      return process;
    }

    private static int gitDaemonPort(Path tempDir) throws IOException {
      return Integer.parseInt(new String(Files.readAllBytes(tempDir.resolve("git-daemon.port")), StandardCharsets.UTF_8).trim());
    }

    private static String buildRemoteUri(Path bareRepo, int port) {
      return "git://127.0.0.1:" + port + "/" + bareRepo.getFileName();
    }

    private static int reservePort() throws IOException {
      try (ServerSocket serverSocket = new ServerSocket(0)) {
        return serverSocket.getLocalPort();
      }
    }

    private static void waitForDaemon(int port) throws Exception {
      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
      while (System.currentTimeMillis() < deadline) {
        try (Socket ignored = new Socket("127.0.0.1", port)) {
          return;
        } catch (IOException exception) {
          Thread.sleep(100L);
        }
      }
      throw new IllegalStateException("Git daemon failed to start on port " + port);
    }

    private static void stopGitDaemon(Process process) {
      if (process == null) {
        return;
      }
      process.destroy();
      try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
    }

    private static String sanitizeBranch(String branchName) {
      return branchName.replace('/', '-');
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
}
