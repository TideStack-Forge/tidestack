package com.ouroboros.dev.app.service.runtime;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.List;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.exception.BusinessException;

@Component
public class LogicFlowDebugHelperArtifactMaterializer {
  private static final String ARTIFACT_ID = "ouroboros-logicflow-debug-helper";
  private static final String ARTIFACT_FILE_NAME = "ouroboros-logicflow-debug-helper.jar";
  private static final String POM_PROPERTIES = "META-INF/maven/com.ouroboros/ouroboros-logicflow-debug-helper/pom.properties";

  private final DevelopWorkspaceContext developWorkspaceContext;

  public LogicFlowDebugHelperArtifactMaterializer(DevelopWorkspaceContext developWorkspaceContext) {
    this.developWorkspaceContext = developWorkspaceContext;
  }

  public Try<Path> materialize() {
    return Try.of(this::doMaterialize);
  }

  private Path doMaterialize() throws Exception {
    String developSupportFilesDir = developWorkspaceContext.getDevelopSupportFilesDir();
    if (StringUtils.isBlank(developSupportFilesDir)) {
      return null;
    }
    Path source = Paths.get(developSupportFilesDir, ARTIFACT_FILE_NAME);
    if (!Files.isRegularFile(source)) {
      return null;
    }
    verifyArtifact(source);
    var commandArtifacts = List.of("ouroboros-dev-helper-core", "ouroboros-dev-helper-runtime", "ouroboros-common-dev");
    for (String artifact : commandArtifacts) {
      if (!Files.isRegularFile(source.getParent().resolve(artifact + ".jar"))) {
        throw new BusinessException("LogicFlow debug helper 缺少命令通道依赖: " + artifact);
      }
    }

    if (StringUtils.isBlank(developWorkspaceContext.getDebugSupportFilesDir())) {
      throw new BusinessException("LogicFlow debug helper 目标目录不能为空");
    }
    Path targetRoot = Paths.get(developWorkspaceContext.getDebugSupportFilesDir());
    Files.createDirectories(targetRoot);
    Path target = targetRoot.resolve(ARTIFACT_FILE_NAME);
    copyAtomically(source, target);
    for (String artifact : commandArtifacts) {
      copyAtomically(source.getParent().resolve(artifact + ".jar"), targetRoot.resolve(artifact + ".jar"));
    }
    return target;
  }

  private void copyAtomically(Path source, Path target) throws java.io.IOException {
    Path staging = Files.createTempFile(target.getParent(), ".debug-helper-", ".jar");
    try {
      Files.copy(source, staging, StandardCopyOption.REPLACE_EXISTING);
      Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(staging);
    }
  }

  private void verifyArtifact(Path source) throws Exception {
    try (JarFile jarFile = new JarFile(source.toFile())) {
      var entry = jarFile.getJarEntry(POM_PROPERTIES);
      if (entry == null) {
        throw new BusinessException("LogicFlow debug helper artifact 缺少 Maven metadata: " + POM_PROPERTIES);
      }
      Properties properties = new Properties();
      try (InputStream inputStream = jarFile.getInputStream(entry)) {
        properties.load(inputStream);
      }
      String artifactId = properties.getProperty("artifactId");
      if (!ARTIFACT_ID.equals(artifactId)) {
        throw new BusinessException("LogicFlow debug helper artifactId 不匹配: " + artifactId);
      }
    }
  }
}
