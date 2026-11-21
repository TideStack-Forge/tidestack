package com.ouroboros.devhelper.codeenvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.jar.JarInputStream;
import com.ouroboros.devhelper.command.CodeEnvironmentCommand;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationCodeEnvironmentTest {
  @TempDir Path directory;
  @Test void exportsDirectoryBytesWithoutLoadingClassesAndRejectsUnownedReads() throws Exception {
    Path classes = directory.resolve("classes"); Files.createDirectories(classes);
    Files.write(classes.resolve("NeverLoad.class"), new byte[] {1, 2, 3});
    try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, null);
         var environment = new ApplicationCodeEnvironment(loader)) {
      var command = new CodeEnvironmentCommand(environment);
      var retired = command.execute(new CommandRequest().setParams(Map.of("helperInstanceId", "retired", "action", "manifest"))).join();
      assertFalse(retired.getSuccess());
      var request = new CommandRequest().setParams(Map.of("helperInstanceId", HelperRuntimeIdentity.current().getInstanceId(), "action", "manifest"));
      var result = command.execute(request).join(); assertTrue(result.getSuccess(), result.getMessage());
      var manifest = (CodeEnvironmentManifest) result.getData();
      assertEquals(1, manifest.artifacts().size());
      var artifact = manifest.artifacts().getFirst();
      byte[] bytes = environment.read(manifest.version(), artifact.sha256(), 0);
      try (var jar = new JarInputStream(new java.io.ByteArrayInputStream(bytes))) {
        assertEquals("NeverLoad.class", jar.getNextJarEntry().getName()); assertArrayEquals(new byte[] {1, 2, 3}, jar.readAllBytes());
      }
      assertThrows(IllegalArgumentException.class, () -> environment.read(manifest.version(), "../../secret", 0));
      assertThrows(IllegalArgumentException.class, () -> environment.read("old", artifact.sha256(), 0));
      assertThrows(IllegalArgumentException.class, () -> environment.read(manifest.version(), artifact.sha256(), -1));
      assertThrows(IllegalArgumentException.class, () -> environment.read(manifest.version(), artifact.sha256(), artifact.size()));
      assertSame(manifest, environment.manifest());
    }
  }
  @Test void includesParentLoaderClassesAndFlattensBootPackageRootsAndNestedJars() throws Exception {
    Path parent = directory.resolve("parent"); Files.createDirectories(parent);
    Files.write(parent.resolve("Parent.class"), new byte[] {4});
    Files.write(parent.resolve("Shared.class"), new byte[] {7});
    var nested = new java.io.ByteArrayOutputStream();
    try (var jar = new java.util.jar.JarOutputStream(nested)) {
      jar.putNextEntry(new java.util.jar.JarEntry("probe/Nested.class")); jar.write(new byte[] {5}); jar.closeEntry();
    }
    Path boot = directory.resolve("boot.jar");
    try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(boot))) {
      jar.putNextEntry(new java.util.jar.JarEntry("BOOT-INF/classes/Shared.class")); jar.write(new byte[] {8}); jar.closeEntry();
      jar.putNextEntry(new java.util.jar.JarEntry("BOOT-INF/classes/probe/Boot.class")); jar.write(new byte[] {6}); jar.closeEntry();
      jar.putNextEntry(new java.util.jar.JarEntry("BOOT-INF/lib/nested.jar")); jar.write(nested.toByteArray()); jar.closeEntry();
    }
    try (var parentLoader = new URLClassLoader(new java.net.URL[] {parent.toUri().toURL()}, null);
         var loader = new URLClassLoader(new java.net.URL[] {boot.toUri().toURL()}, parentLoader);
         var environment = new ApplicationCodeEnvironment(loader)) {
      var manifest = environment.manifest();
      var names = new java.util.ArrayList<String>();
      var duplicates = new java.util.ArrayList<Integer>();
      for (var artifact : manifest.artifacts()) {
        try (var jar = new JarInputStream(new java.io.ByteArrayInputStream(environment.read(manifest.version(), artifact.sha256(), 0)))) {
          for (java.util.jar.JarEntry entry; (entry = jar.getNextJarEntry()) != null;) { names.add(entry.getName()); if ("Shared.class".equals(entry.getName())) duplicates.add(jar.read()); }
        }
      }
      assertEquals(java.util.List.of(7, 8), duplicates);
      assertTrue(names.contains("Parent.class"), names.toString());
      assertTrue(names.contains("probe/Boot.class"), names.toString());
      assertTrue(names.contains("probe/Nested.class"), names.toString());
      assertFalse(names.stream().anyMatch(name -> name.startsWith("BOOT-INF/")), names.toString());
    }
  }
  @Test void deduplicatesIdenticalDirectoryArtifactsAndIncludesTheSystemParent() throws Exception {
    Path classes = directory.resolve("child"); Files.createDirectories(classes);
    Files.write(classes.resolve("Child.class"), new byte[] {1});
    try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getSystemClassLoader());
         var environment = new ApplicationCodeEnvironment(loader)) {
      var manifest = environment.manifest();
      assertTrue(manifest.artifacts().size() > 1, "The actual system parent classpath must be exported");
      assertEquals(manifest.artifacts().size(), manifest.artifacts().stream().map(CodeEnvironmentManifest.Artifact::sha256).distinct().count());
    }
    Path other = directory.resolve("other"); Files.createDirectories(other);
    Files.write(other.resolve("Child.class"), new byte[] {1});
    try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL(), other.toUri().toURL()}, null);
         var environment = new ApplicationCodeEnvironment(loader)) {
      assertEquals(1, environment.manifest().artifacts().size());
    }
  }
}
