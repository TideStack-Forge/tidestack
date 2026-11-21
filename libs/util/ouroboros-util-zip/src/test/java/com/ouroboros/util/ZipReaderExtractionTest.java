package com.ouroboros.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

class ZipReaderExtractionTest {

  @TempDir
  Path temporaryDirectory;

  @TestFactory
  List<DynamicTest> extractionResults() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean stream : List.of(false, true)) {
      String source = stream ? "stream" : "file";
      tests.add(DynamicTest.dynamicTest(source + " extracts all entries", () -> {
        Path output = temporaryDirectory.resolve(source + "-success");
        Try<Void> result = extract(stream, archive("a.txt", "b.txt"), output, ZipReaderExtractionTest::copy);
        assertTrue(result.isSuccess());
        assertEquals("a.txt", Files.readString(output.resolve("a.txt")));
        assertEquals("b.txt", Files.readString(output.resolve("b.txt")));
      }));
      tests.add(DynamicTest.dynamicTest(source + " reports rejected handler and retains partial output", () -> {
        Path output = temporaryDirectory.resolve(source + "-handler-failure");
        Try<Void> result = extract(stream, archive("a.txt", "b.txt"), output, (entry, target) -> {
          copy(entry, target);
          return !entry.getName().equals("a.txt");
        });
        assertTrue(result.isFailure());
        assertEquals(1, result.getCause().getSuppressed().length);
        assertTrue(result.getCause().getSuppressed()[0].getMessage().contains("a.txt"));
        assertEquals("a.txt", Files.readString(output.resolve("a.txt")));
        assertEquals("b.txt", Files.readString(output.resolve("b.txt")));
      }));
      tests.add(DynamicTest.dynamicTest(source + " reports entry I/O failure and continues other entries", () -> {
        Path output = temporaryDirectory.resolve(source + "-io-failure");
        Files.createDirectories(output.resolve("a.txt"));
        Try<Void> result = extract(stream, archive("a.txt", "b.txt"), output, ZipReaderExtractionTest::copy);
        assertTrue(result.isFailure());
        Throwable[] failures = result.getCause().getSuppressed();
        assertEquals(1, failures.length);
        assertInstanceOf(IOException.class, failures[0].getCause());
        assertTrue(failures[0].getMessage().contains("a.txt"));
        assertEquals("b.txt", Files.readString(output.resolve("b.txt")));
      }));
      tests.add(DynamicTest.dynamicTest(source + " accepts an empty archive", () -> {
        Try<Void> result = extract(stream, archive(), temporaryDirectory.resolve(source + "-empty"),
            ZipReaderExtractionTest::copy);
        assertTrue(result.isSuccess());
      }));
    }
    return tests;
  }

  @Test
  void missingArchiveReturnsFailure() {
    Try<Void> result = new ZipReader().extractFile(
        temporaryDirectory.resolve("missing.zip").toFile(), temporaryDirectory.resolve("missing-output").toString());
    assertTrue(result.isFailure());
    assertInstanceOf(IOException.class, result.getCause());
  }

  @Test
  void unreadableArchiveStreamReturnsOriginalFailure() {
    IOException failure = new IOException("archive unavailable");
    InputStream input = new InputStream() {
      @Override
      public int read() throws IOException {
        throw failure;
      }
    };
    Try<Void> result = new ZipReader().extractFile(input, temporaryDirectory.resolve("unreadable").toString());
    assertTrue(result.isFailure());
    assertEquals(failure, result.getCause());
  }

  private Try<Void> extract(boolean stream, byte[] bytes, Path output,
      BiFunction<ZipReader.ZipEntryWrapper, OutputStream, Boolean> handler) throws IOException {
    ZipReader reader = new ZipReader();
    if (stream) {
      return reader.extractFile(new ByteArrayInputStream(bytes), output.toString(), handler);
    }
    Path archive = temporaryDirectory.resolve(output.getFileName() + ".zip");
    Files.write(archive, bytes);
    return reader.extractFile(archive.toFile(), output.toString(), handler);
  }

  private static boolean copy(ZipReader.ZipEntryWrapper entry, OutputStream target) {
    try {
      entry.copyTo(target);
      return true;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static byte[] archive(String... entries) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (String name : entries) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(name.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}
