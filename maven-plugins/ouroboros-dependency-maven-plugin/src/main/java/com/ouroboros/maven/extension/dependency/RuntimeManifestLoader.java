package com.ouroboros.maven.extension.dependency;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;

final class RuntimeManifestLoader {
  private final ObjectMapper objectMapper = new ObjectMapper();

  RuntimeManifest load(String manifestUrl) throws IOException {
    try (InputStream inputStream = open(manifestUrl)) {
      return objectMapper.readValue(inputStream, RuntimeManifest.class);
    }
  }

  private static InputStream open(String manifestUrl) throws IOException {
    if (manifestUrl.startsWith("http://")
        || manifestUrl.startsWith("https://")
        || manifestUrl.startsWith("file:")) {
      return new URL(manifestUrl).openStream();
    }
    File file = Paths.get(manifestUrl).toFile();
    return Files.newInputStream(file.toPath());
  }
}
