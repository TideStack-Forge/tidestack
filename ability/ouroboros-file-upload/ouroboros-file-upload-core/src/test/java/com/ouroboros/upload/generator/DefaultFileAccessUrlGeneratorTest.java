package com.ouroboros.upload.generator;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultFileAccessUrlGeneratorTest {
  @Test void encodesEachKeySegmentOnceAndRetainsAbsoluteServicePrefix() {
    var generator = new DefaultFileAccessUrlGenerator();
    assertEquals("https://cdn.example/bucket/a/%E7%A9%BA%20%E6%A0%BC%23%25%3F.txt", generator.generateUrl("a/空 格#%?.txt", Map.of("fileUrlPrefix", "https://cdn.example/bucket/")));
    assertEquals("/api/file/a/b", generator.generateUrl("a/b", Map.of("fileUrlPrefix", "/api/file/")));
    assertEquals("/a/b", generator.generateUrl("a/b", Map.of()));
    assertThrows(RuntimeException.class, () -> generator.generateUrl("../escape", Map.of()));
  }
}
