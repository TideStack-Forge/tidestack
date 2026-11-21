package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.artifact.TemplateArchiveReader;
import com.ouroboros.util.ZipLimits;

class TemplateArchiveReaderSecurityTest {

  @Test
  void shouldReadOnlyCanonicalRegularTemplateEntries() throws Exception {
    byte[] archive = zip(Map.of(
        "template.json", CatalogTestSupport.utf8("{}"),
        "tree/src/index.ts", CatalogTestSupport.utf8("export {};")
    ));

    Map<String, byte[]> entries = new TemplateArchiveReader(ZipLimits.strict()).read(new ByteArrayInputStream(archive));

    assertArrayEquals(CatalogTestSupport.utf8("{}"), entries.get("template.json"));
    assertTrue(entries.containsKey("tree/src/index.ts"));
  }

  @Test
  void shouldRejectTraversalDuplicateCaseAndSymlinkEntries() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> new TemplateArchiveReader(ZipLimits.strict()).read(
        new ByteArrayInputStream(zip(Map.of("../evil.txt", CatalogTestSupport.utf8("x"))))
    ));
    Map<String, byte[]> duplicateCase = new LinkedHashMap<>();
    duplicateCase.put("tree/File.txt", CatalogTestSupport.utf8("a"));
    duplicateCase.put("tree/file.txt", CatalogTestSupport.utf8("b"));
    assertThrows(IllegalArgumentException.class, () -> new TemplateArchiveReader(ZipLimits.strict()).read(
        new ByteArrayInputStream(zip(duplicateCase))
    ));
    assertThrows(IllegalArgumentException.class, () -> new TemplateArchiveReader(ZipLimits.strict()).read(
        new ByteArrayInputStream(symlinkZip())
    ));
  }

  @Test
  void shouldRejectTotalExpandedSizeOverLimit() throws Exception {
    ZipLimits tiny = ZipLimits.custom()
        .maxEntrySize(10)
        .maxTotalSize(3)
        .maxEntries(10)
        .build();

    assertThrows(IllegalArgumentException.class, () -> new TemplateArchiveReader(tiny).read(
        new ByteArrayInputStream(zip(Map.of(
            "tree/a.txt", CatalogTestSupport.utf8("aa"),
            "tree/b.txt", CatalogTestSupport.utf8("bb")
        )))
    ));
  }

  private static byte[] zip(Map<String, byte[]> entries) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  private static byte[] symlinkZip() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
      ZipArchiveEntry entry = new ZipArchiveEntry("tree/link");
      entry.setUnixMode(0120000 | 0777);
      zip.putArchiveEntry(entry);
      zip.write("target".getBytes(StandardCharsets.UTF_8));
      zip.closeArchiveEntry();
    }
    return bytes.toByteArray();
  }
}
