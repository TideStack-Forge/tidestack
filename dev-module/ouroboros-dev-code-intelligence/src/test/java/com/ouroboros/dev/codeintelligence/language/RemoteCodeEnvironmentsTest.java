package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.codeenvironment.CodeEnvironmentManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RemoteCodeEnvironmentsTest {
  @TempDir Path directory;
  private final DevHelperCommandService commands = mock(DevHelperCommandService.class);
  private final AtomicInteger reads = new AtomicInteger();
  private final byte[] content = new byte[200000];

  private CodeEnvironmentManifest prepare(boolean corrupt) throws Exception {
    new java.util.Random(42).nextBytes(content);
    String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    var artifacts = List.of(new CodeEnvironmentManifest.Artifact(digest, content.length));
    var manifest = new CodeEnvironmentManifest(com.ouroboros.codeintelligence.runtime.ContentVersion.of(List.of(21, artifacts)), 21, artifacts);
    when(commands.exec(eq("app"), eq("codeEnvironment"), anyMap(), anyLong())).thenAnswer(invocation -> {
      Map<?, ?> params = invocation.getArgument(2);
      Object data;
      if ("manifest".equals(params.get("action"))) data = manifest;
      else {
        reads.incrementAndGet(); int offset = ((Number) params.get("offset")).intValue();
        byte[] chunk = java.util.Arrays.copyOfRange(content, offset, Math.min(content.length, offset + 131072));
        if (corrupt) chunk[0]++;
        data = java.util.Base64.getEncoder().encodeToString(chunk);
      }
      return CompletableFuture.completedFuture(new CommandResult().setSuccess(true).setData(data));
    });
    return manifest;
  }
  @Test void reconstructsRemoteBytesDeduplicatesConcurrentRequestsAndReusesVerifiedCache() throws Exception {
    var manifest = prepare(false);
    var resolver = new RemoteCodeEnvironments(commands, directory, 1000000, List.of());
    var identity = Map.<String,Object>of("helperInstanceId", "one");
    var a = resolver.resolve("app", identity); var b = resolver.resolve("app", identity);
    assertSame(a, b);
    var environment = a.join();
    assertEquals(manifest.version(), environment.version());
    assertArrayEquals(content, Files.readAllBytes(environment.classpath().getFirst()));
    assertEquals(2, reads.get());
    verify(commands).exec(eq("app"), eq("codeEnvironment"), argThat(params -> "manifest".equals(params.get("action"))), eq(90_000L));
    verify(commands, times(2)).exec(eq("app"), eq("codeEnvironment"), argThat(params -> "read".equals(params.get("action"))), eq(30_000L));
    resolver.resolve("app", Map.of("helperInstanceId", "two")).join(); assertEquals(2, reads.get());
    Files.write(environment.classpath().getFirst(), new byte[] {0});
    resolver.resolve("app", Map.of("helperInstanceId", "three")).join(); assertEquals(4, reads.get());
  }
  @Test void rejectsCorruptionCacheExhaustionAndMissingIdentityWithoutLeavingPartialFiles() throws Exception {
    prepare(true);
    var resolver = new RemoteCodeEnvironments(commands, directory, 1000000, List.of());
    assertThrows(RuntimeException.class, () -> resolver.resolve("app", Map.of("helperInstanceId", "one")).join());
    try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    assertThrows(RuntimeException.class, () -> resolver.resolve("app", Map.of()).join());
    var full = new RemoteCodeEnvironments(commands, directory, 100, List.of());
    assertThrows(RuntimeException.class, () -> full.resolve("app", Map.of("helperInstanceId", "one")).join());
  }
  @Test void matchingLocalContentAvoidsTransferWithoutTrustingLocalPathNames() throws Exception {
    prepare(false); Path same = directory.resolve("local.jar"); Files.write(same, content);
    var resolver = new RemoteCodeEnvironments(commands, directory.resolve("cache"), 1000000, List.of(same));
    var environment = resolver.resolve("app", Map.of("helperInstanceId", "one")).join();
    assertEquals(0, reads.get()); assertNotEquals(same, environment.classpath().getFirst());
    Files.delete(same); assertArrayEquals(content, Files.readAllBytes(environment.classpath().getFirst()));
  }
}
