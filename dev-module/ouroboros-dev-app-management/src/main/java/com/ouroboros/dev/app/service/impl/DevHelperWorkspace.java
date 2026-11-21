package com.ouroboros.dev.app.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.exception.PlatformException;

/** Owns the physical helper workspace and staging directory lifecycle. */
final class DevHelperWorkspace {
  private static final String HELPER_PROFILE = "dev-helper";
  private final DevelopWorkspaceContext developWorkspaceContext;

  DevHelperWorkspace(DevelopWorkspaceContext developWorkspaceContext) {
    this.developWorkspaceContext = developWorkspaceContext;
  }

  Try<Void> prepare(String appName) {
    Path appMetadataSourceDir = Paths.get(developWorkspaceContext.getDevRootDir(), appName);
    Path devSupportFilesDir = Paths.get(developWorkspaceContext.getDevelopSupportFilesDir());
    Path vcsSupportDir = devSupportFilesDir.resolve("vcs").normalize();
    Path appRunDir = Paths.get(developWorkspaceContext.getRunRootDir(), appName, HELPER_PROFILE);
    Path containerMetadataDir = Paths.get(appRunDir.toString(), "metadata");
    Path containerExtendDir = Paths.get(appRunDir.toString(), "extend");

    Try<Void> metadataCopy = mirrorDirectory(appMetadataSourceDir, containerMetadataDir, null, "拷贝开发助手应用元数据失败");
    if (metadataCopy.isFailure()) {
      return metadataCopy;
    }

    if (!Files.exists(devSupportFilesDir)) {
      return Try.success(null);
    }

    return mirrorDirectory(devSupportFilesDir, containerExtendDir, vcsSupportDir, "拷贝开发支持文件失败");
  }

  private static Try<Void> mirrorDirectory(Path sourceDir, Path targetDir, Path excludedRoot, String errorMessage) {
    Try<Void> copy = Try.run(() -> {
      if (targetDir.getParent() != null) {
        Files.createDirectories(targetDir.getParent());
      }
      Path stagingDir = Files.createTempDirectory(targetDir.getParent(), ".helper-workspace-staging-");
      try {
        if (Files.exists(sourceDir)) {
          try (java.util.stream.Stream<Path> walk = Files.walk(sourceDir)) {
            walk.forEach(sourcePath -> {
              if (Files.isSymbolicLink(sourcePath)) {
                return;
              }
              if (excludedRoot != null && sourcePath.normalize().startsWith(excludedRoot)) {
                return;
              }
              Path targetPath = stagingDir.resolve(sourceDir.relativize(sourcePath));
              if (Files.isDirectory(sourcePath)) {
                if (Files.notExists(targetPath)) {
                  createDirectories(targetPath);
                }
                return;
              }
              copyPath(sourcePath, targetPath);
            });
          }
        }
        if (Files.exists(targetDir)) {
          try (java.util.stream.Stream<Path> walk = Files.walk(targetDir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> deletePath(path));
          }
        }
        Files.move(stagingDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception exception) {
        if (Files.exists(stagingDir)) {
          try (java.util.stream.Stream<Path> walk = Files.walk(stagingDir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> deletePath(path));
          }
        }
        throw exception;
      }
    });
    if (copy.isFailure()) {
      return Try.failure(new PlatformException(errorMessage, copy.getCause()));
    }
    return Try.success(null);
  }

  private static void deletePath(Path path) {
    try {
      Files.delete(path);
    } catch (Exception exception) {
      throw new PlatformException("删除开发支持路径失败", exception);
    }
  }

  private static void createDirectories(Path path) {
    try {
      Files.createDirectories(path);
    } catch (Exception exception) {
      throw new PlatformException("创建开发支持目录失败", exception);
    }
  }

  private static void copyPath(Path sourcePath, Path targetPath) {
    try {
      if (targetPath.getParent() != null) {
        Files.createDirectories(targetPath.getParent());
      }
      Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception exception) {
      throw new PlatformException("复制开发支持文件失败", exception);
    }
  }

}
