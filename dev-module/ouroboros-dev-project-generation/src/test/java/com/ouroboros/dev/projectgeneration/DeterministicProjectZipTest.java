package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.render.RenderedProjectTemplate;
import com.ouroboros.dev.projectgeneration.zip.DeterministicProjectZipWriter;

class DeterministicProjectZipTest {

  private final DeterministicProjectZipWriter writer = new DeterministicProjectZipWriter();

  @Test
  void shouldWriteStableSortedZipWithFixedTimestampsAndCleanup() throws Exception {
    AtomicInteger closed = new AtomicInteger();

    ZipResult first = writeOnce(closed);
    ZipResult second = writeOnce(closed);

    assertArrayEquals(first.bytes(), second.bytes());
    assertEquals(first.sha256(), second.sha256());
    assertEquals(sha256(first.bytes()), first.sha256());
    assertEquals("customer-portal-1.0.0.zip", first.filename());
    assertEquals(List.of(
        "customer-portal/",
        "customer-portal/.ouroboros-template.json",
        "customer-portal/README.md",
        "customer-portal/src/index.ts"
    ), first.entryNames());
    assertTrue(first.entryTimes().stream().allMatch(time -> time == 0L));
    assertEquals(2, closed.get());
  }

  private ZipResult writeOnce(AtomicInteger closed) throws Exception {
    Path path;
    byte[] bytes;
    String sha256;
    String filename;
    try (GeneratedProject generatedProject = writer.write(renderedTemplate(), "1.0.0", closed::incrementAndGet)) {
      path = generatedProject.path();
      bytes = Files.readAllBytes(path);
      sha256 = generatedProject.sha256();
      filename = generatedProject.filename();
      assertEquals(bytes.length, generatedProject.size());
      assertTrue(Files.exists(path));
    }
    assertFalse(Files.exists(path.getParent()));
    return new ZipResult(filename, bytes, sha256, entryNames(bytes), entryTimes(bytes));
  }

  private static RenderedProjectTemplate renderedTemplate() {
    return new RenderedProjectTemplate(
        "customer-portal",
        Map.of(
            "customer-portal/src/index.ts", bytes("export default 'customer';"),
            "customer-portal/README.md", bytes("# Customer Portal"),
            "customer-portal/.ouroboros-template.json", bytes("{\"contractVersion\":\"1\"}")
        )
    );
  }

  private static List<String> entryNames(byte[] bytes) throws Exception {
    List<String> names = new ArrayList<>();
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      var entry = input.getNextEntry();
      while (entry != null) {
        names.add(entry.getName());
        entry = input.getNextEntry();
      }
    }
    return names;
  }

  private static List<Long> entryTimes(byte[] bytes) throws Exception {
    List<Long> times = new ArrayList<>();
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      var entry = input.getNextEntry();
      while (entry != null) {
        times.add(entry.getTime());
        entry = input.getNextEntry();
      }
    }
    return times;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private record ZipResult(
      String filename,
      byte[] bytes,
      String sha256,
      List<String> entryNames,
      List<Long> entryTimes
  ) {
  }
}
