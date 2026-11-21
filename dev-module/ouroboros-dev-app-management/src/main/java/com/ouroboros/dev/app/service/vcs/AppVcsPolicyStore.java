package com.ouroboros.dev.app.service.vcs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.util.Json;

@Component
public class AppVcsPolicyStore {
  static final String POLICY_RELATIVE_PATH = Paths.get(".ouroboros", "vcs", "policy.json").toString();

  private final AppHomeResolver appHomeResolver;

  public AppVcsPolicyStore(AppHomeResolver appHomeResolver) {
    this.appHomeResolver = appHomeResolver;
  }

  public AppVcsPolicy load(String appName) {
    return Try.of(() -> doLoad(appName)).get();
  }

  public AppVcsPolicy loadSanitized(String appName) {
    return AppVcsRemoteSupport.sanitizePolicyForApi(load(appName));
  }

  public Try<Void> save(String appName, AppVcsPolicy policy) {
    return Try.run(() -> doSave(appName, policy));
  }

  private AppVcsPolicy doLoad(String appName) throws Exception {
    Path policyPath = policyPath(appName);
    if (!Files.exists(policyPath)) {
      return new AppVcsPolicy();
    }
    String content = new String(Files.readAllBytes(policyPath), StandardCharsets.UTF_8);
    if (StringUtils.isBlank(content)) {
      return new AppVcsPolicy();
    }
    return Json.tryToBean(AppVcsPolicy.class, content)
        .getOrElseThrow(throwable -> new IllegalStateException("读取应用VCS策略失败: " + appName, throwable));
  }

  private void doSave(String appName, AppVcsPolicy policy) throws Exception {
    AppVcsPolicy normalized = AppVcsRemoteSupport.normalizePolicy(policy);
    Path policyPath = policyPath(appName);
    if (isEmpty(normalized)) {
      Files.deleteIfExists(policyPath);
      return;
    }
    Files.createDirectories(policyPath.getParent());
    
    // Atomic write: stage to temp file, then move
    Path tempFile = Files.createTempFile(policyPath.getParent(), ".policy-", ".json.tmp");
    try {
      Files.write(tempFile, Json.toPrettyJsonString(normalized).getBytes(StandardCharsets.UTF_8));
      Files.move(tempFile, policyPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      Files.deleteIfExists(tempFile);
      throw e;
    }
  }

  private boolean isEmpty(AppVcsPolicy policy) {
    return StringUtils.isBlank(policy.getDefaultRemote())
        && (policy.getRemotes() == null || policy.getRemotes().isEmpty())
        && StringUtils.isBlank(policy.getRemoteName())
        && StringUtils.isBlank(policy.getRemoteUri());
  }

  private Path policyPath(String appName) {
    return Paths.get(appHomeResolver.getAppHomeByAppId(appName), POLICY_RELATIVE_PATH)
        .toAbsolutePath()
        .normalize();
  }
}
