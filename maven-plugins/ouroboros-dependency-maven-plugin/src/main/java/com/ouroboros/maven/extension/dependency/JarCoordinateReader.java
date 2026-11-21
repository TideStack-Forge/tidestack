package com.ouroboros.maven.extension.dependency;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class JarCoordinateReader {
  ArtifactCoordinate read(File jarFile) throws IOException {
    try (JarFile file = new JarFile(jarFile)) {
      JarEntry entry = findPomProperties(file);
      if (entry == null) {
        return new ArtifactCoordinate(null, null, null);
      }
      Properties properties = new Properties();
      try (InputStream inputStream = file.getInputStream(entry)) {
        properties.load(inputStream);
      }
      return new ArtifactCoordinate(
          properties.getProperty("groupId"),
          properties.getProperty("artifactId"),
          properties.getProperty("version"));
    }
  }

  private static JarEntry findPomProperties(JarFile file) {
    Enumeration<JarEntry> entries = file.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      String name = entry.getName();
      if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
        return entry;
      }
    }
    return null;
  }
}
