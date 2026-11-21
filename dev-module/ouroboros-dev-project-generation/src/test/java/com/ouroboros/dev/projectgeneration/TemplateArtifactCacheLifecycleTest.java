package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.projectgeneration.repository.TemplateArtifactRepository;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateArtifactDescriptor;

class TemplateArtifactCacheLifecycleTest {
  @TempDir
  Path cacheDir;

  @Test
  void shouldRemoveStartupPartFilesAndEvictOldUnpinnedEntries() throws Exception {
    Files.write(cacheDir.resolve("leftover.part"), CatalogTestSupport.utf8("tmp"));
    Path old = cacheDir.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.zip");
    Path newer = cacheDir.resolve("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.zip");
    Files.write(old, CatalogTestSupport.utf8("aaa"));
    Files.write(newer, CatalogTestSupport.utf8("bbb"));
    Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1));
    Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2));

    repository(new TemplateArtifactRepositoryTest.FakeHttpClient(), 1024, 3).recoverStartup();

    assertFalse(Files.exists(cacheDir.resolve("leftover.part")));
    assertFalse(Files.exists(old));
    assertTrue(Files.exists(newer));
  }

  @Test
  void shouldKeepPinnedArtifactsAndRejectNewFetchWhenNoEvictableSpaceRemains() throws Exception {
    byte[] first = CatalogTestSupport.utf8("zip1");
    byte[] second = CatalogTestSupport.utf8("zip2");
    TemplateArtifactRepositoryTest.FakeHttpClient httpClient = new TemplateArtifactRepositoryTest.FakeHttpClient()
        .enqueue(200, first)
        .enqueue(200, second);
    TemplateArtifactRepository repository = repository(httpClient, 1024, first.length);
    ProjectTemplateArtifactDescriptor firstDescriptor = descriptor(first);
    ProjectTemplateArtifactDescriptor secondDescriptor = descriptor(second);

    try (var pinned = repository.fetch(firstDescriptor)) {
      assertThrows(IllegalStateException.class, () -> repository.fetch(secondDescriptor));
      assertTrue(Files.exists(pinned.path()));
      assertFalse(Files.exists(cacheDir.resolve(secondDescriptor.sha256() + ".zip")));
    }

    assertEquals(2, httpClient.calls());
  }

  private TemplateArtifactRepository repository(
      TemplateArtifactRepositoryTest.FakeHttpClient httpClient,
      long maxArtifactBytes,
      long maxCacheBytes) {
    return new TemplateArtifactRepository(
        cacheDir,
        com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy.exactOrigin(
            "https://templates.example.com",
            java.util.List.of()
        ),
        httpClient,
        maxArtifactBytes,
        maxCacheBytes,
        0,
        java.time.Duration.ofMillis(100),
        java.time.Duration.ofMillis(100)
    );
  }

  private static ProjectTemplateArtifactDescriptor descriptor(byte[] body) {
    return new ProjectTemplateArtifactDescriptor(
        ProjectGenerationBatch5TestSupport.artifactUri(),
        com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier.sha256Hex(body),
        body.length
    );
  }
}
