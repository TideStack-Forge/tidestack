package com.ouroboros.dev.app.service.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

@Component
public class RuntimeLockOptInInspector {
  private final DevelopWorkspaceContext workspaceContext;

  public RuntimeLockOptInInspector(DevelopWorkspaceContext workspaceContext) {
    this.workspaceContext = workspaceContext;
  }

  public boolean isOptedIn(String appName, String profile) {
    Path appRoot = Paths.get(workspaceContext.getDevRootDir(), appName, "runtime-lock");
    if (hasEnabledAppBindings(appRoot.resolve("app-artifact-bindings.json"))) {
      return true;
    }
    Path overlayPath = appRoot.resolve(Paths.get("profiles", profileStorageKey(profile), "artifact-overlays.json"));
    return hasArrayEntries(overlayPath);
  }

  private String profileStorageKey(String profile) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(profile.getBytes(StandardCharsets.UTF_8));
  }

  private boolean hasEnabledAppBindings(Path path) {
    if (!Files.exists(path)) {
      return false;
    }
    try {
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      List<Map<String, Object>> bindings = Json.toBean(List.class, content);
      if (bindings == null) {
        return false;
      }
      for (Map<String, Object> binding : bindings) {
        if (binding == null) {
          continue;
        }
        if (Boolean.TRUE.equals(binding.get("enabled"))) {
          return true;
        }
      }
      return false;
    } catch (Exception error) {
      throw new BusinessException("读取应用运行时锁绑定失败", error);
    }
  }

  private boolean hasArrayEntries(Path path) {
    if (!Files.exists(path)) {
      return false;
    }
    try {
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      List<Object> entries = Json.toBean(List.class, content);
      return entries != null && !entries.isEmpty();
    } catch (Exception error) {
      throw new BusinessException("读取运行时锁Profile覆盖失败", error);
    }
  }
}
