package com.ouroboros.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

class ZipWriterFixedTimestampTest {

  @Test
  void shouldKeepDefaultTimestampBehaviorWhenFixedTimestampIsUnset() throws Exception {
    byte[] bytes = zipBytes(new ZipWriter.ZipWriterConfig(), Map.of("a.txt", "a".getBytes()));

    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      var entry = input.getNextEntry();
      assertEquals("a.txt", entry.getName());
      assertTrue(entry.getTime() > Instant.EPOCH.toEpochMilli());
    }
  }

  @Test
  void shouldProduceStableBytesWhenFixedTimestampIsSet() throws Exception {
    ZipWriter.ZipWriterConfig config = new ZipWriter.ZipWriterConfig()
        .setCompressionLevel(Deflater.BEST_COMPRESSION)
        .setFixedEntryTimestamp(Instant.EPOCH);

    byte[] first = zipBytes(config, Map.of("b.txt", "b".getBytes(), "a.txt", "a".getBytes()));
    byte[] second = zipBytes(config, Map.of("b.txt", "b".getBytes(), "a.txt", "a".getBytes()));

    assertArrayEquals(first, second);
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(first))) {
      var entry = input.getNextEntry();
      assertEquals(Instant.EPOCH.toEpochMilli(), entry.getTime());
    }
  }

  private static byte[] zipBytes(ZipWriter.ZipWriterConfig config, Map<String, byte[]> entries) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipWriter writer = new ZipWriter(output, config)) {
      for (Map.Entry<String, byte[]> entry : new java.util.TreeMap<>(entries).entrySet()) {
        writer.addEntry(entry.getKey(), entry.getValue());
      }
      writer.finish();
    }
    return output.toByteArray();
  }
}
