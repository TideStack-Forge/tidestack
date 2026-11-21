package com.ouroboros.resource.runtime.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.resource.model.ResourceScope;

class ClasspathResourceProviderTest {

  private final ClasspathResourceProvider provider = new ClasspathResourceProvider();

  @TempDir
  Path tempDir;

  @Test
  void doesNotBundleSampleResourcesOnDefaultClasspath() {
    assertEquals(Collections.emptyList(), provider.listResources("frontend"));
    assertEquals(Collections.emptyList(), provider.listResources("backend"));
    assertFalse(provider.getResource(ResourceScope.FRONTEND, "example.txt").isPresent());
    assertFalse(provider.getResource(ResourceScope.BACKEND, "config.properties").isPresent());
  }

  @Test
  void loadsResourcesFromExplicitClasspathRoot() throws Exception {
    Path classpathRoot = tempDir.resolve("classpath-root");
    writeFile(classpathRoot,
        "META-INF/ouroboros-resources/frontend/example.txt",
        "frontend-example");
    writeFile(classpathRoot,
        "META-INF/ouroboros-resources/backend/config.properties",
        "backend-config=true");

    try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[] {classpathRoot.toUri().toURL()}, null)) {
      ClasspathResourceProvider scopedProvider = new ClasspathResourceProvider(classLoader);
      assertTrue(scopedProvider.getResource(ResourceScope.FRONTEND, "example.txt").isPresent());
      assertTrue(scopedProvider.getResource(ResourceScope.BACKEND, "config.properties").isPresent());
    }
  }

  @Test
  void classpathListingsIgnoreIndexEntriesWithoutBackingFiles() throws Exception {
    Path classpathRoot = tempDir.resolve("classpath-root");
    writeFile(classpathRoot,
        "META-INF/ouroboros-resources/frontend/templates/actual.txt",
        "actual-template");
    writeFile(classpathRoot,
        "META-INF/ouroboros-resources/frontend/templates/index.json",
        "{\n"
            + "  \"path\": \"frontend/templates\",\n"
            + "  \"resources\": [\n"
            + "    { \"name\": \"actual.txt\" },\n"
            + "    { \"name\": \"ghost.txt\" }\n"
            + "  ]\n"
            + "}");

    try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[] {classpathRoot.toUri().toURL()}, null)) {
      ClasspathResourceProvider scopedProvider = new ClasspathResourceProvider(classLoader);
      assertEquals(Collections.singletonList("frontend/templates/actual.txt"),
          scopedProvider.listResources("frontend/templates"));
    }
  }

  private static void writeFile(Path root, String relativePath, String content) throws Exception {
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }
}
