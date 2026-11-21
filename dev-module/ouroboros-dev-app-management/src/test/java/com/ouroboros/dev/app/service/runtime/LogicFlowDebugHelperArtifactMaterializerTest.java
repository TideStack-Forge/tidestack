package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogicFlowDebugHelperArtifactMaterializerTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldSkipMaterializationWhenDevelopSupportArtifactIsMissing() {
    LogicFlowDebugHelperArtifactMaterializer materializer = new LogicFlowDebugHelperArtifactMaterializer(
        RuntimeFactoryTestSupport.workspaceContext(tempDir)
    );

    Path copied = materializer.materialize().get();

    assertNull(copied);
    assertFalse(Files.exists(tempDir.resolve("debug-support")));
  }

  @Test
  void shouldCopyDevelopSupportArtifactIntoDebugSupportRoot() throws Exception {
    writeArtifactJar("ouroboros-logicflow-debug-helper");
    for (String dependency : java.util.List.of("ouroboros-dev-helper-core", "ouroboros-dev-helper-runtime", "ouroboros-common-dev")) {
      Files.write(tempDir.resolve("develop-support/" + dependency + ".jar"), new byte[] {1});
    }
    LogicFlowDebugHelperArtifactMaterializer materializer = new LogicFlowDebugHelperArtifactMaterializer(
        RuntimeFactoryTestSupport.workspaceContext(tempDir)
    );

    Path copied = materializer.materialize().get();

    assertEquals(tempDir.resolve("debug-support").resolve("ouroboros-logicflow-debug-helper.jar"), copied);
    assertTrue(Files.exists(copied));
  }

  @Test
  void shouldRejectDifferentDevelopSupportArtifact() throws Exception {
    writeArtifactJar("not-logicflow-debug-helper");
    LogicFlowDebugHelperArtifactMaterializer materializer = new LogicFlowDebugHelperArtifactMaterializer(
        RuntimeFactoryTestSupport.workspaceContext(tempDir)
    );

    assertTrue(materializer.materialize().isFailure());
    assertFalse(Files.exists(tempDir.resolve("debug-support").resolve("ouroboros-logicflow-debug-helper.jar")));
  }

  private Path writeArtifactJar(String artifactId) throws Exception {
    Path sourceJar = tempDir.resolve("develop-support/ouroboros-logicflow-debug-helper.jar");
    Files.createDirectories(sourceJar.getParent());
    try (OutputStream outputStream = Files.newOutputStream(sourceJar);
         JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
      jarOutputStream.putNextEntry(new JarEntry("META-INF/maven/com.ouroboros/ouroboros-logicflow-debug-helper/pom.properties"));
      String metadata = "groupId=com.ouroboros\n"
          + "artifactId=" + artifactId + "\n"
          + "version=2.0.0-test\n";
      jarOutputStream.write(metadata.getBytes(StandardCharsets.UTF_8));
      jarOutputStream.closeEntry();
    }
    return sourceJar;
  }
}
