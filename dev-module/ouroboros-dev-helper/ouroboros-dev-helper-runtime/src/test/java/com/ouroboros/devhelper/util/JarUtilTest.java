package com.ouroboros.devhelper.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class JarUtilTest {

  @Test
  void shouldReadVersionFromPomProperties() throws IOException {
    InputStream inStream = new ByteArrayInputStream("version=1.2.3\n".getBytes(StandardCharsets.UTF_8));

    assertEquals("1.2.3", JarUtil.readHelperVersion(inStream));
  }

  @Test
  void shouldReturnUnknownWhenStreamMissing() throws IOException {
    assertEquals("unknown", JarUtil.readHelperVersion(null));
  }

  @Test
  void shouldReturnUnknownWhenVersionMissing() throws IOException {
    InputStream inStream = new ByteArrayInputStream("artifactId=helper\n".getBytes(StandardCharsets.UTF_8));

    assertEquals("unknown", JarUtil.readHelperVersion(inStream));
  }

  @Test
  void shouldPropagateRuntimeFailureFromReader() {
    InputStream inStream = new InputStream() {
      @Override
      public int read() {
        throw new IndexOutOfBoundsException("broken nested jar stream");
      }
    };

    assertThrows(IndexOutOfBoundsException.class, () -> JarUtil.readHelperVersion(inStream));
  }

  @Test
  void shouldReturnUnknownWhenResourceAccessFails() {
    assertEquals("unknown", JarUtil.getHelperVersion(() -> {
      throw new IndexOutOfBoundsException("broken nested jar stream");
    }));
  }
}
