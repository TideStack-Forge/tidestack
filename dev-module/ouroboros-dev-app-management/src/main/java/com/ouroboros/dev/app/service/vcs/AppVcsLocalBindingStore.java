package com.ouroboros.dev.app.service.vcs;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSConfig;

@Component
public class AppVcsLocalBindingStore {
  private static final String VCS_SUPPORT_DIR = "vcs";
  private static final String BINDINGS_FILE = "bindings.json";
  private static final String CREDENTIALS_FILE = "credentials.json";
  private static final String KEYS_DIR = "keys";
  private static final String DEFAULT_SSH_KEY_NAME = "id_rsa";
  private static final String DEFAULT_REMOTE_NAME = "origin";

  private final DevelopWorkspaceContext developWorkspaceContext;

  public AppVcsLocalBindingStore(DevelopWorkspaceContext developWorkspaceContext) {
    this.developWorkspaceContext = developWorkspaceContext;
  }

  public AppVcsLocalBinding load(String appName) {
    return Try.of(() -> doLoad(appName)).get();
  }

  public AppVcsLocalBinding loadSanitized(String appName) {
    return AppVcsRemoteSupport.sanitizeBindingForApi(load(appName), null);
  }

  public Try<Void> save(String appName, AppVcsLocalBinding binding) {
    return Try.run(() -> doSave(appName, binding));
  }

  Path credentialsPath(String appName) {
    return appSupportDir(appName).resolve(CREDENTIALS_FILE);
  }

  Path bindingsPath(String appName) {
    return appSupportDir(appName).resolve(BINDINGS_FILE);
  }

  Path keyPath(String appName, String sshKeyName) {
    return keyPath(appName, DEFAULT_REMOTE_NAME, sshKeyName);
  }

  Path keyPath(String appName, String remoteName, String sshKeyName) {
    return remoteKeysDir(appName, remoteName)
        .resolve(normalizePathSegment(sshKeyName, "sshKeyName", DEFAULT_SSH_KEY_NAME));
  }

  private AppVcsLocalBinding doLoad(String appName) throws Exception {
    BindingStateDocument bindingState = readBindingState(appName);
    CredentialStateDocument credentialState = readCredentialState(appName);

    List<AppVcsRemoteBinding> bindings = new ArrayList<>();
    for (BindingDocument entry : bindingState.getResolvedBindings()) {
      AppVcsRemoteBinding binding = new AppVcsRemoteBinding();
      binding.setRemoteName(entry.getRemoteName());
      binding.setAuthType(entry.getAuthType());
      binding.setSshKeyName(entry.getSshKeyName());

      CredentialDocument credential = credentialState.getCredential(entry.getRemoteName());
      if (binding.getAuthType() == VCSConfig.AuthType.HTTP_BASIC && credential != null) {
        binding.setUsername(credential.getUsername());
        binding.setPassword(credential.getPassword());
      }
      if (binding.getAuthType() == VCSConfig.AuthType.SSH_KEY && StringUtils.isNotBlank(binding.getSshKeyName())) {
        Path keyPath = keyPath(appName, binding.getRemoteName(), binding.getSshKeyName());
        if (Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS)) {
          ensureKeyPathNotSymlink(appName, keyPath);
        }
        if (Files.isRegularFile(keyPath, LinkOption.NOFOLLOW_LINKS)) {
          binding.setSshKeyPath(keyPath.toString());
        }
      }
      bindings.add(binding);
    }

    AppVcsLocalBinding loaded = new AppVcsLocalBinding();
    loaded.setBindings(bindings);
    if (!bindings.isEmpty()) {
      AppVcsRemoteBinding firstBinding = bindings.get(0);
      loaded.setRemoteName(firstBinding.getRemoteName());
      loaded.setAuthType(firstBinding.getAuthType());
      loaded.setUsername(firstBinding.getUsername());
      loaded.setPassword(firstBinding.getPassword());
      loaded.setSshKeyName(firstBinding.getSshKeyName());
      loaded.setSshPrivateKey(firstBinding.getSshPrivateKey());
      loaded.setSshKeyPath(firstBinding.getSshKeyPath());
    }
    return loaded;
  }

  private BindingStateDocument readBindingState(String appName) throws Exception {
    Path bindingsPath = bindingsPath(appName);
    if (!Files.exists(bindingsPath)) {
      return new BindingStateDocument();
    }
    return Json.tryToBean(BindingStateDocument.class,
            new String(Files.readAllBytes(bindingsPath), StandardCharsets.UTF_8))
        .getOrElseThrow(throwable -> new IllegalStateException("读取本地VCS绑定失败: " + appName, throwable));
  }

  private CredentialStateDocument readCredentialState(String appName) throws Exception {
    Path credentialsPath = credentialsPath(appName);
    if (!Files.exists(credentialsPath)) {
      return new CredentialStateDocument();
    }
    return Json.tryToBean(CredentialStateDocument.class,
            new String(Files.readAllBytes(credentialsPath), StandardCharsets.UTF_8))
        .getOrElseThrow(throwable -> new IllegalStateException("读取本地VCS凭证失败: " + appName, throwable));
  }

  private void doSave(String appName, AppVcsLocalBinding binding) throws Exception {
    AppVcsLocalBinding normalized = AppVcsRemoteSupport.normalizeBinding(binding);
    List<AppVcsRemoteBinding> bindings = materializeExistingSshKeyContent(appName, AppVcsRemoteSupport.resolveBindings(normalized));
    BindingStateDocument bindingState = new BindingStateDocument();
    CredentialStateDocument credentialState = new CredentialStateDocument();
    List<KeyMaterial> keyMaterials = new ArrayList<>();

    for (AppVcsRemoteBinding currentBinding : bindings) {
      saveBinding(appName, currentBinding, bindingState, credentialState, keyMaterials);
    }

    Path appDir = appSupportDir(appName);
    Path supportRoot = appDir.getParent();
    Files.createDirectories(supportRoot);
    Path stagedDir = Files.createTempDirectory(supportRoot, normalizePathSegment(appName, "appName", null) + ".staged-");
    applyOwnerOnlyDirectoryPermissions(stagedDir);

    try {
      writeStagedState(stagedDir, bindingState, credentialState, keyMaterials);
      replaceAppSupportDir(appDir, stagedDir);
    } catch (Exception exception) {
      deleteRecursively(stagedDir);
      throw exception;
    }
  }

  private List<AppVcsRemoteBinding> materializeExistingSshKeyContent(String appName, List<AppVcsRemoteBinding> bindings) throws Exception {
    List<AppVcsRemoteBinding> materialized = new ArrayList<>();
    for (AppVcsRemoteBinding binding : bindings) {
      AppVcsRemoteBinding current = new AppVcsRemoteBinding();
      current.setRemoteName(binding.getRemoteName());
      current.setAuthType(binding.getAuthType());
      current.setUsername(binding.getUsername());
      current.setPassword(binding.getPassword());
      current.setSshKeyName(binding.getSshKeyName());
      current.setSshKeyPath(binding.getSshKeyPath());
      String sshPrivateKey = StringUtils.trimToNull(binding.getSshPrivateKey());
      if (binding.getAuthType() == VCSConfig.AuthType.SSH_KEY && sshPrivateKey == null) {
        Path currentKeyPath = keyPath(appName, binding.getRemoteName(), binding.getSshKeyName());
        if (Files.exists(currentKeyPath, LinkOption.NOFOLLOW_LINKS)) {
          ensureKeyPathNotSymlink(appName, currentKeyPath);
        }
        if (Files.isRegularFile(currentKeyPath, LinkOption.NOFOLLOW_LINKS)) {
          sshPrivateKey = new String(Files.readAllBytes(currentKeyPath), StandardCharsets.UTF_8);
        }
      }
      current.setSshPrivateKey(sshPrivateKey);
      materialized.add(current);
    }
    return materialized;
  }

  private void saveBinding(String appName,
                           AppVcsRemoteBinding binding,
                           BindingStateDocument bindingState,
                           CredentialStateDocument credentialState,
                           List<KeyMaterial> keyMaterials) throws Exception {
    if (binding.getAuthType() == null || binding.getAuthType() == VCSConfig.AuthType.NONE) {
      return;
    }
    String remoteName = normalizePathSegment(binding.getRemoteName(), "remoteName", DEFAULT_REMOTE_NAME);
    if (binding.getAuthType() == VCSConfig.AuthType.HTTP_BASIC) {
      saveHttpBasic(remoteName, binding, bindingState, credentialState);
      return;
    }
    if (binding.getAuthType() == VCSConfig.AuthType.SSH_KEY) {
      saveSshKey(remoteName, binding, bindingState, keyMaterials);
      return;
    }
    throw new BusinessException("暂不支持的认证类型: " + binding.getAuthType());
  }

  private void saveHttpBasic(String remoteName,
                             AppVcsRemoteBinding binding,
                             BindingStateDocument bindingState,
                             CredentialStateDocument credentialState) {
    if (StringUtils.isAnyBlank(binding.getUsername(), binding.getPassword())) {
      throw new BusinessException("HTTP Basic 凭证不能为空");
    }

    BindingDocument bindingDocument = new BindingDocument();
    bindingDocument.setRemoteName(remoteName);
    bindingDocument.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
    bindingState.addBinding(bindingDocument);

    CredentialDocument credentialDocument = new CredentialDocument();
    credentialDocument.setRemoteName(remoteName);
    credentialDocument.setUsername(binding.getUsername());
    credentialDocument.setPassword(binding.getPassword());
    credentialState.addCredential(credentialDocument);
  }

  private void saveSshKey(String remoteName,
                          AppVcsRemoteBinding binding,
                          BindingStateDocument bindingState,
                          List<KeyMaterial> keyMaterials) {
    String sshKeyName = normalizePathSegment(binding.getSshKeyName(), "sshKeyName", DEFAULT_SSH_KEY_NAME);
    String sshPrivateKey = StringUtils.trimToNull(binding.getSshPrivateKey());
    if (sshPrivateKey == null) {
      throw new BusinessException("SSH 私钥不能为空");
    }
    keyMaterials.add(new KeyMaterial(remoteName, sshKeyName, sshPrivateKey));

    BindingDocument bindingDocument = new BindingDocument();
    bindingDocument.setRemoteName(remoteName);
    bindingDocument.setAuthType(VCSConfig.AuthType.SSH_KEY);
    bindingDocument.setSshKeyName(sshKeyName);
    bindingState.addBinding(bindingDocument);
  }

  private Path appSupportDir(String appName) {
    String safeAppName = normalizePathSegment(appName, "appName", null);
    Path baseDir = Paths.get(developWorkspaceContext.getDevelopSupportFilesDir(), VCS_SUPPORT_DIR)
        .toAbsolutePath()
        .normalize();
    Path resolved = baseDir.resolve(safeAppName).normalize();
    if (!resolved.startsWith(baseDir)) {
      throw new BusinessException("appName 非法");
    }
    return resolved;
  }

  private Path keysDir(String appName) {
    return appSupportDir(appName).resolve(KEYS_DIR);
  }

  private Path remoteKeysDir(String appName, String remoteName) {
    return keysDir(appName).resolve(normalizePathSegment(remoteName, "remoteName", DEFAULT_REMOTE_NAME));
  }

  private void ensureKeyPathNotSymlink(String appName, Path keyPath) {
    Path appDir = appSupportDir(appName).toAbsolutePath().normalize();
    Path normalizedKeyPath = keyPath.toAbsolutePath().normalize();
    if (!normalizedKeyPath.startsWith(appDir)) {
      throw new BusinessException("SSH 私钥路径非法");
    }
    Path current = normalizedKeyPath;
    while (current != null && current.startsWith(appDir)) {
      if (Files.isSymbolicLink(current)) {
        throw new BusinessException("检测到符号链接，禁止复用已有 SSH 私钥");
      }
      if (current.equals(appDir)) {
        break;
      }
      current = current.getParent();
    }
  }

  private String normalizePathSegment(String value, String fieldName, String defaultValue) {
    String normalized = StringUtils.trimToNull(value);
    if (normalized == null) {
      normalized = defaultValue;
    }
    if (normalized == null) {
      throw new BusinessException(fieldName + " 不能为空");
    }
    if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
      throw new BusinessException(fieldName + " 非法");
    }
    return normalized;
  }

  private void deleteRecursively(Path path) throws Exception {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(current -> {
            try {
              Files.deleteIfExists(current);
            } catch (Exception exception) {
              throw new InternalException("删除应用 VCS 本地绑定文件失败", exception);
            }
          });
    }
  }

  private void writeStagedState(Path stagedDir,
                                BindingStateDocument bindingState,
                                CredentialStateDocument credentialState,
                                List<KeyMaterial> keyMaterials) throws Exception {
    if (!bindingState.getResolvedBindings().isEmpty()) {
      Path bindingsPath = stagedDir.resolve(BINDINGS_FILE);
      Files.write(bindingsPath, Json.toPrettyJsonString(bindingState).getBytes(StandardCharsets.UTF_8));
      applyOwnerOnlyFilePermissions(bindingsPath);
    }
    if (!credentialState.getResolvedCredentials().isEmpty()) {
      Path credentialsPath = stagedDir.resolve(CREDENTIALS_FILE);
      Files.write(credentialsPath, Json.toPrettyJsonString(credentialState).getBytes(StandardCharsets.UTF_8));
      applyOwnerOnlyFilePermissions(credentialsPath);
    }
    for (KeyMaterial keyMaterial : keyMaterials) {
      Path keyPath = stagedDir.resolve(KEYS_DIR)
          .resolve(normalizePathSegment(keyMaterial.getRemoteName(), "remoteName", DEFAULT_REMOTE_NAME))
          .resolve(normalizePathSegment(keyMaterial.getSshKeyName(), "sshKeyName", DEFAULT_SSH_KEY_NAME));
      Files.createDirectories(keyPath.getParent());
      applyOwnerOnlyDirectoryPermissions(keyPath.getParent());
      Files.write(keyPath, keyMaterial.getSshPrivateKey().getBytes(StandardCharsets.UTF_8));
      applyOwnerOnlyFilePermissions(keyPath);
    }
  }

  private void replaceAppSupportDir(Path appDir, Path stagedDir) throws Exception {
    boolean hadExistingState = Files.exists(appDir);
    Path backupDir = hadExistingState
        ? Files.createTempDirectory(appDir.getParent(), normalizePathSegment(appDir.getFileName().toString(), "appName", null) + ".backup-")
        : null;
    try {
      if (hadExistingState) {
        movePath(appDir, backupDir);
      }
      movePath(stagedDir, appDir);
      deleteRecursively(backupDir);
    } catch (Exception exception) {
      if (Files.exists(appDir)) {
        deleteRecursively(appDir);
      }
      if (hadExistingState && backupDir != null && Files.exists(backupDir)) {
        movePath(backupDir, appDir);
      }
      throw exception;
    }
  }

  private void movePath(Path source, Path target) throws Exception {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target);
    }
  }

  private void applyOwnerOnlyDirectoryPermissions(Path path) throws Exception {
    if (!supportsPosixPermissions(path)) {
      return;
    }
    Files.setPosixFilePermissions(path, EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    ));
  }

  private void applyOwnerOnlyFilePermissions(Path path) throws Exception {
    if (!supportsPosixPermissions(path)) {
      return;
    }
    Files.setPosixFilePermissions(path, EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    ));
  }

  private boolean supportsPosixPermissions(Path path) {
    return Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null;
  }

  private static class KeyMaterial {
    private final String remoteName;
    private final String sshKeyName;
    private final String sshPrivateKey;

    private KeyMaterial(String remoteName, String sshKeyName, String sshPrivateKey) {
      this.remoteName = remoteName;
      this.sshKeyName = sshKeyName;
      this.sshPrivateKey = sshPrivateKey;
    }

    private String getRemoteName() {
      return remoteName;
    }

    private String getSshKeyName() {
      return sshKeyName;
    }

    private String getSshPrivateKey() {
      return sshPrivateKey;
    }
  }

  public static class BindingStateDocument {
    private String remoteName;
    private VCSConfig.AuthType authType = VCSConfig.AuthType.NONE;
    private String sshKeyName;
    private List<BindingDocument> bindings = new ArrayList<>();

    public List<BindingDocument> getBindings() {
      return bindings;
    }

    public void setBindings(List<BindingDocument> bindings) {
      this.bindings = bindings;
    }

    public void addBinding(BindingDocument binding) {
      if (binding == null) {
        return;
      }
      if (bindings == null) {
        bindings = new ArrayList<>();
      }
      bindings.add(binding);
    }

    public List<BindingDocument> getResolvedBindings() {
      if (bindings != null && !bindings.isEmpty()) {
        return bindings;
      }
      if (authType == null || authType == VCSConfig.AuthType.NONE) {
        return Collections.emptyList();
      }
      BindingDocument binding = new BindingDocument();
      binding.setRemoteName(StringUtils.defaultIfBlank(StringUtils.trimToNull(remoteName), DEFAULT_REMOTE_NAME));
      binding.setAuthType(authType);
      binding.setSshKeyName(sshKeyName);
      return Collections.singletonList(binding);
    }

    public String getRemoteName() {
      return remoteName;
    }

    public void setRemoteName(String remoteName) {
      this.remoteName = remoteName;
    }

    public VCSConfig.AuthType getAuthType() {
      return authType;
    }

    public void setAuthType(VCSConfig.AuthType authType) {
      this.authType = authType == null ? VCSConfig.AuthType.NONE : authType;
    }

    public String getSshKeyName() {
      return sshKeyName;
    }

    public void setSshKeyName(String sshKeyName) {
      this.sshKeyName = sshKeyName;
    }
  }

  public static class BindingDocument {
    private String remoteName;
    private VCSConfig.AuthType authType = VCSConfig.AuthType.NONE;
    private String sshKeyName;

    public String getRemoteName() {
      return remoteName;
    }

    public void setRemoteName(String remoteName) {
      this.remoteName = remoteName;
    }

    public VCSConfig.AuthType getAuthType() {
      return authType;
    }

    public void setAuthType(VCSConfig.AuthType authType) {
      this.authType = authType == null ? VCSConfig.AuthType.NONE : authType;
    }

    public String getSshKeyName() {
      return sshKeyName;
    }

    public void setSshKeyName(String sshKeyName) {
      this.sshKeyName = sshKeyName;
    }
  }

  public static class CredentialStateDocument {
    private String remoteName;
    private String username;
    private String password;
    private List<CredentialDocument> credentials = new ArrayList<>();

    public List<CredentialDocument> getCredentials() {
      return credentials;
    }

    public void setCredentials(List<CredentialDocument> credentials) {
      this.credentials = credentials;
    }

    public void addCredential(CredentialDocument credential) {
      if (credential == null) {
        return;
      }
      if (credentials == null) {
        credentials = new ArrayList<>();
      }
      credentials.add(credential);
    }

    public CredentialDocument getCredential(String remoteName) {
      return getResolvedCredentials().stream()
          .filter(current -> StringUtils.equals(current.getRemoteName(), remoteName))
          .findFirst()
          .orElse(null);
    }

    public List<CredentialDocument> getResolvedCredentials() {
      if (credentials != null && !credentials.isEmpty()) {
        return credentials;
      }
      if (StringUtils.isAllBlank(username, password)) {
        return Collections.emptyList();
      }
      CredentialDocument credential = new CredentialDocument();
      credential.setRemoteName(StringUtils.defaultIfBlank(StringUtils.trimToNull(remoteName), DEFAULT_REMOTE_NAME));
      credential.setUsername(username);
      credential.setPassword(password);
      return Collections.singletonList(credential);
    }

    public String getRemoteName() {
      return remoteName;
    }

    public void setRemoteName(String remoteName) {
      this.remoteName = remoteName;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class CredentialDocument {
    private String remoteName;
    private String username;
    private String password;

    public String getRemoteName() {
      return remoteName;
    }

    public void setRemoteName(String remoteName) {
      this.remoteName = remoteName;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}
