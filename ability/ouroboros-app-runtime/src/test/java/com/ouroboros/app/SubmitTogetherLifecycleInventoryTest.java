package com.ouroboros.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmitTogetherLifecycleInventoryTest {
  private static final String EMPTY_TREE_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @TempDir
  Path tempDir;

  @Test
  void verifierSelfTestShouldPass() throws Exception {
    ProcessResult result = runVerifier("--self-test");

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("self-test passed"));
  }

  @Test
  void verifierShouldFailWhenSourceIsIncomplete() throws Exception {
    Path inventory = tempDir.resolve("inventory.json");
    Files.writeString(inventory, """
        {
          "schemaVersion": "submit-together-lifecycle-inventory/v1",
          "sourceComplete": false,
          "sourceLedger": [{"type": "repository", "sourceComplete": true}],
          "entries": []
        }
        """, StandardCharsets.UTF_8);

    ProcessResult result = runVerifier("--inventory", inventory.toString());

    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("sourceComplete must be true"));
  }

  @Test
  void verifierShouldRejectEmptyRepositoryScanEvidence() throws Exception {
    Path inventory = tempDir.resolve("empty-repository.json");
    Files.writeString(inventory, """
        {
          "schemaVersion": "submit-together-lifecycle-inventory/v1",
          "sourceComplete": true,
          "sourceLedger": [{
            "type": "repository",
            "sourceComplete": true,
            "repositoryHash": "%s",
            "scannedFileCount": 0
          }],
          "entries": []
        }
        """.formatted(EMPTY_TREE_SHA256), StandardCharsets.UTF_8);

    ProcessResult result = runVerifier("--inventory", inventory.toString());

    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("empty scan evidence"));
  }

  @Test
  void verifierShouldRejectAcceptedSecurityHook() throws Exception {
    Path inventory = tempDir.resolve("security.json");
    Files.writeString(inventory, """
        {
          "schemaVersion": "submit-together-lifecycle-inventory/v1",
          "sourceComplete": true,
          "sourceLedger": [{"type": "repository", "sourceComplete": true}],
          "entries": [{
            "id": "customer.contacts",
            "issues": [{
              "id": "access-control",
              "category": "security",
              "severity": "blocking",
              "resolution": "accepted-behavior-removal",
              "approvalRef": "owner-approval-2026-07-31"
            }]
          }]
        }
        """, StandardCharsets.UTF_8);

    ProcessResult result = runVerifier("--inventory", inventory.toString());

    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("must be migrated"));
  }

  @Test
  void verifierShouldAcceptBusinessRemovalAndPerformanceFallbackWithApproval() throws Exception {
    Path inventory = tempDir.resolve("valid.json");
    Files.writeString(inventory, """
        {
          "schemaVersion": "submit-together-lifecycle-inventory/v1",
          "sourceComplete": true,
          "sourceLedger": [{"type": "repository", "sourceComplete": true}],
          "entries": [{
            "id": "customer.contacts",
            "issues": [{
              "id": "custom-after-script",
              "category": "business-script",
              "severity": "important",
              "resolution": "accepted-behavior-removal",
              "approvalRef": "owner-approval-2026-07-31"
            }, {
              "id": "single-item-plugin",
              "category": "data-plugin-fallback",
              "severity": "important",
              "resolution": "accepted-performance-fallback",
              "approvalRef": "owner-approval-2026-07-31"
            }]
          }]
        }
        """, StandardCharsets.UTF_8);

    ProcessResult result = runVerifier("--inventory", inventory.toString());

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("inventory verification passed"));
  }

  @Test
  void verifierShouldAcceptResolutionsAlias() throws Exception {
    Path inventory = tempDir.resolve("unresolved.json");
    Files.writeString(inventory, """
        {
          "schemaVersion": "submit-together-lifecycle-inventory/v1",
          "sourceComplete": true,
          "sourceLedger": [{"type": "repository", "sourceComplete": true}],
          "entries": [{
            "id": "customer.contacts",
            "issues": [{
              "id": "custom-after-script",
              "category": "business-script",
              "severity": "important",
              "resolution": "pending"
            }]
          }]
        }
        """, StandardCharsets.UTF_8);
    Path resolutions = tempDir.resolve("resolutions.json");
    Files.writeString(resolutions, """
        {
          "issues": {
            "custom-after-script": {
              "resolution": "accepted-behavior-removal",
              "approvalRef": "owner-approval-2026-07-31"
            }
          }
        }
        """, StandardCharsets.UTF_8);

    ProcessResult result = runVerifier(
        "--inventory", inventory.toString(),
        "--resolutions", resolutions.toString());

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("inventory verification passed"));
  }

  @Test
  void runnerShouldGenerateCompleteSourceLedgerForEmptyAppHome() throws Exception {
    Path artifact = tempDir.resolve("app.jar");
    Files.writeString(artifact, "test application artifact", StandardCharsets.UTF_8);
    Path appHome = Files.createDirectory(tempDir.resolve("appHome"));
    Path output = tempDir.resolve("inventory.json");

    ProcessResult runner = runRunner(
        "--app-jar", artifact.toString(),
        "--app-home", appHome.toString(),
        "--repository-root", findRepoRoot().toString(),
        "--output", output.toString());

    assertEquals(0, runner.exitCode(), runner.output());
    String inventory = Files.readString(output, StandardCharsets.UTF_8);
    assertTrue(inventory.contains("\"sourceComplete\": true"), inventory);
    assertTrue(inventory.contains("\"type\": \"provider-spi\""), inventory);
    assertTrue(inventory.contains("\"type\": \"typed-runtime-source\""), inventory);
    assertFalse(EMPTY_TREE_SHA256.equals(extractString(inventory, "repositoryHash")), inventory);

    ProcessResult verifier = runVerifier("--inventory", output.toString());

    assertEquals(0, verifier.exitCode(), verifier.output());
  }

  @Test
  void runnerShouldScanRepositoryRootWhenAbsolutePathContainsWorktrees() throws Exception {
    Path artifact = tempDir.resolve("app.jar");
    Files.writeString(artifact, "test application artifact", StandardCharsets.UTF_8);
    Path appHome = Files.createDirectory(tempDir.resolve("appHome-worktree"));
    Path repositoryRoot = Files.createDirectories(tempDir.resolve(".worktrees/fake-repository"));
    Files.writeString(repositoryRoot.resolve("README.md"), "fake repository", StandardCharsets.UTF_8);
    Path output = tempDir.resolve("worktree-inventory.json");

    ProcessResult runner = runRunner(
        "--app-jar", artifact.toString(),
        "--app-home", appHome.toString(),
        "--repository-root", repositoryRoot.toString(),
        "--output", output.toString());

    assertEquals(0, runner.exitCode(), runner.output());
    String inventory = Files.readString(output, StandardCharsets.UTF_8);
    assertFalse(EMPTY_TREE_SHA256.equals(extractString(inventory, "repositoryHash")), inventory);
    assertTrue(inventory.contains("\"scannedFileCount\": 1"), inventory);
  }

  @Test
  void runnerShouldEmitBlockingIssueForSubmitTogetherAppHomeMetadata() throws Exception {
    Path artifact = tempDir.resolve("app.jar");
    Files.writeString(artifact, "test application artifact", StandardCharsets.UTF_8);
    Path appHome = Files.createDirectory(tempDir.resolve("appHome-with-submitTogether"));
    Files.writeString(appHome.resolve("customer.json"), """
        {
          "fullName": "customer",
          "relations": [{
            "name": "contacts",
            "type": "child",
            "model": "customerContact",
            "referenceKey": "customerId",
            "submitTogether": true
          }]
        }
        """, StandardCharsets.UTF_8);
    Path output = tempDir.resolve("blocking-inventory.json");

    ProcessResult runner = runRunner(
        "--app-artifact", artifact.toString(),
        "--app-home", appHome.toString(),
        "--repository-root", findRepoRoot().toString(),
        "--output", output.toString());

    assertEquals(0, runner.exitCode(), runner.output());
    String inventory = Files.readString(output, StandardCharsets.UTF_8);
    assertTrue(inventory.contains("runtime-diagnostics-required"), inventory);

    ProcessResult verifier = runVerifier("--inventory", output.toString());

    assertEquals(1, verifier.exitCode(), verifier.output());
    assertTrue(verifier.output().contains("is unresolved"));
  }

  private ProcessResult runVerifier(String... args) throws Exception {
    Path script = findRepoRoot().resolve("scripts/migrations/verify-submit-together-lifecycle-inventory.py");
    List<String> command = new java.util.ArrayList<>();
    command.add("python3");
    command.add(script.toString());
    command.addAll(List.of(args));
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new ProcessResult(process.waitFor(), output);
  }

  private ProcessResult runRunner(String... args) throws Exception {
    Path script = findRepoRoot().resolve("scripts/migrations/submit-together-lifecycle-inventory.sh");
    List<String> command = new java.util.ArrayList<>();
    command.add("bash");
    command.add(script.toString());
    command.addAll(List.of(args));
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new ProcessResult(process.waitFor(), output);
  }

  private Path findRepoRoot() throws IOException {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.exists(current.resolve("scripts/migrations/verify-submit-together-lifecycle-inventory.py"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IOException("repository root not found");
  }

  private String extractString(String json, String fieldName) {
    var matcher = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
    if (!matcher.find()) {
      throw new AssertionError("missing field " + fieldName + " in " + json);
    }
    return matcher.group(1);
  }

  private record ProcessResult(int exitCode, String output) {
  }
}
