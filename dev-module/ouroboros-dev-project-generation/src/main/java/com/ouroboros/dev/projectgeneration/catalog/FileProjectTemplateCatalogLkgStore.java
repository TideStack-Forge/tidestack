package com.ouroboros.dev.projectgeneration.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class FileProjectTemplateCatalogLkgStore implements ProjectTemplateCatalogLkgStore {

  private static final int MAGIC = 0x4f50474c;
  private static final int VERSION = 1;
  private static final int MAX_BYTES = 16 * 1024 * 1024;

  private final Path file;

  public FileProjectTemplateCatalogLkgStore(Path file) {
    if (file == null) {
      throw new IllegalArgumentException("LKG file path is required");
    }
    this.file = file;
  }

  @Override
  public Optional<LastKnownGoodCatalog> load() {
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    try {
      byte[] encoded = Files.readAllBytes(file);
      if (encoded.length > MAX_BYTES) {
        throw new IllegalStateException("LKG catalog file exceeds byte limit");
      }
      return Optional.of(decode(encoded));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read LKG catalog file", e);
    }
  }

  @Override
  public void save(LastKnownGoodCatalog catalog) {
    if (catalog == null) {
      throw new IllegalArgumentException("LKG catalog is required");
    }
    Path directory = file.getParent();
    Path temp = tempFilePath();
    try {
      if (directory != null) {
        Files.createDirectories(directory);
      }
      Files.write(temp, encode(catalog));
      moveAtomically(temp);
    } catch (IOException e) {
      cleanupFailedTempFile(temp, e);
      throw new IllegalStateException("Cannot persist LKG catalog file", e);
    }
  }

  private Path tempFilePath() {
    String fileName = file.getFileName().toString();
    return file.resolveSibling(fileName + "." + UUID.randomUUID() + ".part");
  }

  private void moveAtomically(Path temp) throws IOException {
    try {
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException atomicMoveFailure) {
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void cleanupFailedTempFile(Path temp, IOException originalFailure) {
    try {
      Files.deleteIfExists(temp);
    } catch (IOException cleanupFailure) {
      originalFailure.addSuppressed(cleanupFailure);
    }
  }

  private static byte[] encode(LastKnownGoodCatalog catalog) throws IOException {
    byte[] catalogBytes = catalog.catalogBytes();
    byte[] signatureEnvelopeBytes = catalog.signatureEnvelopeBytes();
    if ((long) catalogBytes.length + signatureEnvelopeBytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("LKG catalog exceeds byte limit");
    }
    if (!SignedProjectTemplateCatalogVerifier.sha256Hex(catalogBytes).equals(catalog.digest())) {
      throw new IllegalArgumentException("LKG catalog digest does not match stored bytes");
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(buffer)) {
      output.writeInt(MAGIC);
      output.writeInt(VERSION);
      output.writeLong(catalog.sequence());
      output.writeUTF(catalog.digest());
      output.writeUTF(catalog.keyId());
      output.writeUTF(catalog.verifiedAt().toString());
      output.writeUTF(catalog.expiresAt().toString());
      output.writeInt(catalogBytes.length);
      output.write(catalogBytes);
      output.writeInt(signatureEnvelopeBytes.length);
      output.write(signatureEnvelopeBytes);
    }
    return buffer.toByteArray();
  }

  private static LastKnownGoodCatalog decode(byte[] encoded) throws IOException {
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
      int magic = input.readInt();
      int version = input.readInt();
      if (magic != MAGIC || version != VERSION) {
        throw new IllegalStateException("Unsupported LKG catalog file format");
      }
      long sequence = input.readLong();
      String digest = input.readUTF();
      String keyId = input.readUTF();
      Instant verifiedAt = Instant.parse(input.readUTF());
      Instant expiresAt = Instant.parse(input.readUTF());
      byte[] catalogBytes = readBoundedBytes(input);
      byte[] signatureEnvelopeBytes = readBoundedBytes(input);
      if (input.available() != 0) {
        throw new IllegalStateException("LKG catalog file contains trailing bytes");
      }
      if (!SignedProjectTemplateCatalogVerifier.sha256Hex(catalogBytes).equals(digest)) {
        throw new IllegalStateException("LKG catalog digest does not match stored bytes");
      }
      return new LastKnownGoodCatalog(
          catalogBytes,
          signatureEnvelopeBytes,
          verifiedAt,
          expiresAt,
          sequence,
          digest,
          keyId
      );
    }
  }

  private static byte[] readBoundedBytes(DataInputStream input) throws IOException {
    int length = input.readInt();
    if (length < 0 || length > MAX_BYTES) {
      throw new IllegalStateException("LKG catalog entry exceeds byte limit");
    }
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    return bytes;
  }
}
