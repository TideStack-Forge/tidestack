package com.ouroboros.dev.runtime.kubernetes.workspace;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;
import com.ouroboros.exception.InternalException;

public class PvcWorkspaceProvisioner implements WorkspaceProvisioner {
  private final DevelopWorkspaceContext developWorkspaceContext;
  private final KubernetesRuntimeProperties properties;

  public PvcWorkspaceProvisioner(DevelopWorkspaceContext developWorkspaceContext, KubernetesRuntimeProperties properties) {
    this.developWorkspaceContext = developWorkspaceContext;
    this.properties = properties;
  }

  @Override
  public boolean supports(RuntimeSpec spec) {
    return spec.getBackendType() == BackendType.KUBERNETES && spec.getWorkspaceMode() == WorkspaceMode.PVC;
  }

  @Override
  public Try<WorkspaceRef> prepare(RuntimeSpec spec, Operation operation) {
    String subPath = workspaceSubPath(spec);
    Path runtimeRoot = Paths.get(properties.getMountedWorkspaceRoot(), subPath);
    return materializeWorkspace(spec, runtimeRoot)
        .map(v -> WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.PVC)
            .pvcName(properties.getSharedPvcName())
            .namespace(properties.getNamespace())
            .subPath(subPath)
            .build());
  }

  private Try<Void> materializeWorkspace(RuntimeSpec spec, Path runtimeRoot) {
    if (spec.getRuntimeKind() == RuntimeKind.DEV_HELPER) {
      return mirrorDirectory(Paths.get(developWorkspaceContext.getDevRootDir(), spec.getAppName()), runtimeRoot.resolve("metadata"))
          .flatMap(v -> mirrorDirectory(
              Paths.get(developWorkspaceContext.getDevelopSupportFilesDir()),
              runtimeRoot.resolve("extend"),
              Paths.get(developWorkspaceContext.getDevelopSupportFilesDir()).resolve("vcs")
          ));
    }
    return mirrorDirectory(Paths.get(developWorkspaceContext.getDevRootDir(), spec.getAppName()), runtimeRoot.resolve("metadata"))
        .flatMap(v -> mirrorDirectory(
            Paths.get(developWorkspaceContext.getDebugSupportFilesDir()),
            runtimeRoot.resolve("extend")
        ));
  }

  private Try<Void> mirrorDirectory(Path source, Path target) {
    return mirrorDirectory(source, target, null);
  }

  private Try<Void> mirrorDirectory(Path source, Path target, Path excludedRoot) {
    return Try.run(() -> {
      if (!Files.exists(source)) {
        recreateDirectory(target);
        return;
      }
      
      // Stage to temp directory first
      Files.createDirectories(target.getParent());
      Path tempTarget = Files.createTempDirectory(target.getParent(), ".mirror-staging-");
      try {
        final Path finalTempTarget = tempTarget;
        try (Stream<Path> walk = Files.walk(source)) {
          walk.forEach(sourcePath -> {
            if (Files.isSymbolicLink(sourcePath)) {
              return;
            }
            if (excludedRoot != null && sourcePath.normalize().startsWith(excludedRoot.normalize())) {
              return;
            }
            copyPath(source, sourcePath, finalTempTarget);
          });
        }
        
        // Atomic move: replace target with staged content
        if (Files.exists(target)) {
          try (Stream<Path> walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> deletePath(path));
          }
        }
        Files.move(tempTarget, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception e) {
        if (Files.exists(tempTarget)) {
          try (Stream<Path> walk = Files.walk(tempTarget)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> deletePath(path));
          }
        }
        throw e;
      }
    });
  }

  private void recreateDirectory(Path target) throws IOException {
    if (Files.exists(target)) {
      try (Stream<Path> walk = Files.walk(target)) {
        walk.sorted(Comparator.reverseOrder()).forEach(path -> deletePath(path));
      }
    }
    Files.createDirectories(target);
  }

  private void copyPath(Path sourceRoot, Path sourcePath, Path targetRoot) {
    Path targetPath = targetRoot.resolve(sourceRoot.relativize(sourcePath));
    if (Files.isDirectory(sourcePath)) {
      createDirectories(targetPath);
      return;
    }
    copyFile(sourcePath, targetPath);
  }

  private void deletePath(Path path) {
    try {
      Files.delete(path);
    } catch (IOException exception) {
      throw new InternalException("删除工作区路径失败", exception);
    }
  }

  private void createDirectories(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException exception) {
      throw new InternalException("创建工作区目录失败", exception);
    }
  }

  private void copyFile(Path sourcePath, Path targetPath) {
    try {
      if (targetPath.getParent() != null) {
        Files.createDirectories(targetPath.getParent());
      }
      Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new InternalException("复制工作区文件失败", exception);
    }
  }

  private String workspaceSubPath(RuntimeSpec spec) {
    return "workspace/run/" + spec.getAppName() + "/" + spec.getProfile();
  }
}
