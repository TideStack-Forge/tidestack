package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.projectgeneration.catalog.FileProjectTemplateCatalogLkgStore;
import com.ouroboros.dev.projectgeneration.catalog.LastKnownGoodCatalog;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshot;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;

class ProjectTemplateCatalogDiskLkgStoreTest {

  @TempDir
  private Path tempDir;

  @Test
  void shouldReturnEmptyWhenLkgFileIsMissing() {
    var store = new FileProjectTemplateCatalogLkgStore(tempDir.resolve("catalog.lkg"));

    assertTrue(store.load().isEmpty());
  }

  @Test
  void shouldRoundTripLkgWithAtomicSingleFileWrite() throws Exception {
    var store = new FileProjectTemplateCatalogLkgStore(tempDir.resolve("catalog.lkg"));
    LastKnownGoodCatalog lkg = lkg(90);

    store.save(lkg);
    LastKnownGoodCatalog loaded = store.load().orElseThrow();

    assertEquals(lkg.sequence(), loaded.sequence());
    assertEquals(lkg.digest(), loaded.digest());
    assertEquals(lkg.keyId(), loaded.keyId());
    assertArrayEquals(lkg.catalogBytes(), loaded.catalogBytes());
    assertArrayEquals(lkg.signatureEnvelopeBytes(), loaded.signatureEnvelopeBytes());
    try (Stream<Path> files = Files.list(tempDir)) {
      assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
    }
  }

  @Test
  void shouldRejectMalformedLkgFile() throws Exception {
    Path file = tempDir.resolve("catalog.lkg");
    Files.write(file, CatalogTestSupport.utf8("not a valid lkg"));
    var store = new FileProjectTemplateCatalogLkgStore(file);

    assertThrows(IllegalStateException.class, store::load);
  }

  @Test
  void shouldRejectMismatchedDigestBeforePersisting() {
    var store = new FileProjectTemplateCatalogLkgStore(tempDir.resolve("catalog.lkg"));
    LastKnownGoodCatalog lkg = lkg(91);
    LastKnownGoodCatalog mismatched = new LastKnownGoodCatalog(
        lkg.catalogBytes(),
        lkg.signatureEnvelopeBytes(),
        lkg.verifiedAt(),
        lkg.expiresAt(),
        lkg.sequence(),
        "0000000000000000000000000000000000000000000000000000000000000000",
        lkg.keyId()
    );

    assertThrows(IllegalArgumentException.class, () -> store.save(mismatched));
  }

  private static LastKnownGoodCatalog lkg(long sequence) {
    ProjectTemplateCatalogSnapshot snapshot = snapshot(sequence);
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    return new LastKnownGoodCatalog(
        snapshot.catalogBytes(),
        snapshot.signatureEnvelopeBytes(),
        now,
        snapshot.expiresAt(),
        snapshot.sequence(),
        snapshot.digest(),
        snapshot.signatureKeyId()
    );
  }

  private static ProjectTemplateCatalogSnapshot snapshot(long sequence) {
    java.security.KeyPair keyPair = CatalogTestSupport.ed25519KeyPair();
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    byte[] catalog = CatalogTestSupport.canonicalCatalog(
        sequence,
        "cat_" + sequence + "_abcd",
        now.minusSeconds(60),
        now.plusSeconds(3600)
    );
    byte[] envelope = CatalogTestSupport.utf8(CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, catalog));
    return new SignedProjectTemplateCatalogVerifier(
        Map.of("catalog-key-1", keyPair.getPublic()),
        Clock.fixed(now, ZoneOffset.UTC)
    ).verify(catalog, envelope);
  }
}
