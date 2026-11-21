package com.ouroboros.notification.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.exception.InternalException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextIOUtilsTest {

  @TempDir
  Path tempDir;

  @Test
  void readTextShouldJoinLinesWithLineFeeds() {
    ByteArrayInputStream inputStream = new ByteArrayInputStream("alpha\nbeta".getBytes(StandardCharsets.UTF_8));

    String text = TextIOUtils.readText(inputStream);

    assertEquals("alpha\nbeta", text);
  }

  @Test
  void readTextShouldWrapNullInput() {
    InternalException exception = assertThrows(InternalException.class, () -> TextIOUtils.readText(null));

    assertTrue(exception.getMessage().contains("输入参数异常"));
  }

  @Test
  void writeTextShouldCreateParentDirectoriesAndWriteContent() throws Exception {
    File file = tempDir.resolve("nested/path/config.txt").toFile();

    TextIOUtils.writeText(file, "hello");

    assertTrue(file.isFile());
    assertEquals("hello", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
  }

  @Test
  void writeTextShouldCreateRelativeFileWhenParentIsMissing() throws Exception {
    File file = new File("textio-test-" + UUID.randomUUID() + ".txt");
    try {
      TextIOUtils.writeText(file, "hello");

      assertTrue(file.isFile());
      assertEquals("hello", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    } finally {
      if (file.exists()) {
        if (file.isDirectory()) {
          file.delete();
        } else {
          file.delete();
        }
      }
    }
  }
}
