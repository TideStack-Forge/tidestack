package com.ouroboros.mothership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MothershipInitializationTest {
  @TempDir
  Path workspace;

  @Test
  void deploymentMetadataKeepsExtensionsEnabledAfterRestart() throws Exception {
    Path script = prepare();
    assertEquals("true", run(script, null));
    assertTrue(Files.isSymbolicLink(workspace.resolve("app/metadata")));
    assertEquals("true", run(script, null));
  }

  @Test
  void builtInMetadataKeepsDeploymentExtensionsDisabled() throws Exception {
    Path script = prepare();
    Files.createDirectories(workspace.resolve("app/metadata"));
    assertEquals("false", run(script, null));
    assertEquals("true", run(script, "true"));
  }

  @Test
  void deploymentMetadataRespectsExplicitExtensionDisableAcrossRestarts() throws Exception {
    Path script = prepare();
    assertEquals("false", run(script, "false"));
    assertEquals("false", run(script, "false"));
  }

  private Path prepare() throws Exception {
    Path module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
    String source = Files.readString(module.resolve("docker/root/app/init.sh"));
    Path app = Files.createDirectories(workspace.resolve("app/base/service"));
    Files.createDirectories(workspace.resolve("app/deploy/metadata"));
    Files.writeString(workspace.resolve("hosts"), "127.0.0.1 hostname\n");
    Path start = app.resolve("start.sh");
    Files.writeString(start, "#!/bin/sh\nprintf '%s' \"$ENABLE_DEPLOY_EXTEND\"\n");
    assertTrue(start.toFile().setExecutable(true));
    Path script = workspace.resolve("init.sh");
    Files.writeString(script, source.replace("/app", workspace.resolve("app").toString())
        .replace("/etc/hosts", workspace.resolve("hosts").toString()));
    return script;
  }

  private String run(Path script, String extensions) throws Exception {
    ProcessBuilder builder = new ProcessBuilder("sh", script.toString());
    builder.environment().put("ENABLE_NGINX", "false");
    builder.environment().remove("ENABLE_DEPLOY_EXTEND");
    if (extensions != null) {
      builder.environment().put("ENABLE_DEPLOY_EXTEND", extensions);
    }
    Process process = builder.start();
    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
    if (!finished) process.destroyForcibly();
    assertTrue(finished, "Container initialization must complete");
    String errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.exitValue(), errors);
    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }
}
