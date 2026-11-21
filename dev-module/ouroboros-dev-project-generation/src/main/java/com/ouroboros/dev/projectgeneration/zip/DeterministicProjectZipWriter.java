package com.ouroboros.dev.projectgeneration.zip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Deflater;

import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.render.RenderedProjectTemplate;
import com.ouroboros.util.ZipWriter;

public class DeterministicProjectZipWriter {

  private static final Instant FIXED_ENTRY_TIMESTAMP = Instant.EPOCH;

  public GeneratedProject write(
      RenderedProjectTemplate renderedTemplate,
      String templateVersion,
      Runnable afterClose) {
    if (renderedTemplate == null || templateVersion == null || templateVersion.isBlank()) {
      throw new IllegalArgumentException("Rendered template and template version are required");
    }
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("ouroboros-project-generation-");
      String filename = renderedTemplate.projectName() + "-" + templateVersion + ".zip";
      Path zipPath = tempDir.resolve(filename);
      writeZip(zipPath, renderedTemplate);
      byte[] bytes = Files.readAllBytes(zipPath);
      Path cleanupDir = tempDir;
      return new GeneratedProject(
          filename,
          bytes.length,
          sha256Hex(bytes),
          zipPath,
          Files.newInputStream(zipPath),
          () -> close(cleanupDir, afterClose)
      );
    } catch (IOException e) {
      cleanupAfterFailure(tempDir, e);
      throw new IllegalStateException("Cannot create generated project ZIP", e);
    } catch (RuntimeException e) {
      cleanupAfterFailure(tempDir, e);
      throw e;
    }
  }

  private static void writeZip(Path zipPath, RenderedProjectTemplate renderedTemplate) throws IOException {
    ZipWriter.ZipWriterConfig config = new ZipWriter.ZipWriterConfig()
        .setCompressionLevel(Deflater.BEST_COMPRESSION)
        .setFixedEntryTimestamp(FIXED_ENTRY_TIMESTAMP);
    try (var output = Files.newOutputStream(zipPath);
        ZipWriter writer = new ZipWriter(output, config)) {
      writer.addEmptyDirectory(renderedTemplate.projectName() + "/");
      for (Map.Entry<String, byte[]> entry : new TreeMap<>(renderedTemplate.entries()).entrySet()) {
        writer.addEntry(entry.getKey(), entry.getValue());
      }
      writer.finish();
    }
  }

  private static void close(Path tempDir, Runnable afterClose) {
    RuntimeException failure = null;
    try {
      deleteRecursively(tempDir);
    } catch (RuntimeException e) {
      failure = e;
    } finally {
      if (afterClose != null) {
        try {
          afterClose.run();
        } catch (RuntimeException e) {
          if (failure != null) {
            e.addSuppressed(failure);
          }
          throw e;
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static void cleanupAfterFailure(Path tempDir, Exception failure) {
    if (tempDir == null) {
      return;
    }
    try {
      deleteRecursively(tempDir);
    } catch (RuntimeException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  private static void deleteRecursively(Path root) {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot delete generated project temp directory", e);
    }
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
