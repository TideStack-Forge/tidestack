package com.ouroboros.configuration.provider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.util.ResourceUtil;

class ClassLoaderResourceOrderVerificationTest {

  private static final String PROBE_CLASS_NAME = "verification.OverrideProbe";
  private static final String METADATA_RESOURCE_NAME = "META-INF/ouroboros/metadata/configurations.json";
  private static final String CONFIGURATION_KEY = "verification.override";

  @Test
  void extensionFirstClasspathUsesOverrideResourceOrderSoConfigurationMetadataMergeKeepsExtensionDefaultValue(
      @TempDir Path tempDir) throws Exception {
    Path extensionJar = createProbeJar(tempDir, "extension", "extension-class", "extension-default");
    Path baseJar = createProbeJar(tempDir, "base", "base-class", "base-default");

    try (URLClassLoader classLoader = new URLClassLoader(new URL[] {
        extensionJar.toUri().toURL(),
        baseJar.toUri().toURL()
    }, null)) {
      assertEquals("extension-class", classLoader.loadClass(PROBE_CLASS_NAME).getField("VALUE").get(null));

      URL firstResource = classLoader.getResource(METADATA_RESOURCE_NAME);
      assertNotNull(firstResource);
      assertEquals("extension-default", readSingleMetadata(firstResource).getDefaultValue());

      List<URL> resourceUrls = ResourceUtil.getResourceUrlList(classLoader, METADATA_RESOURCE_NAME);
      List<ConfigurationMetadata> orderedMetadata = readMetadataInResourceOrder(resourceUrls);
      assertEquals(List.of("extension-default", "base-default"), orderedMetadata.stream()
          .map(ConfigurationMetadata::getDefaultValue)
          .toList());

      List<ConfigurationMetadata> overrideOrderedMetadata = readMetadataInResourceOrder(
          ResourceUtil.getResourceUrlListForOverride(classLoader, METADATA_RESOURCE_NAME));
      assertEquals(List.of("base-default", "extension-default"), overrideOrderedMetadata.stream()
          .map(ConfigurationMetadata::getDefaultValue)
          .toList());

      Map<String, ConfigurationMetadata> mergedMetadata = overrideOrderedMetadata.stream()
          .collect(Collectors.toMap(
              ConfigurationMetadata::getKey,
              Function.identity(),
              ConfigurationMetadata::merge,
              () -> new TreeMap<>(String::compareToIgnoreCase)
          ));

      assertEquals("extension-default", mergedMetadata.get(CONFIGURATION_KEY).getDefaultValue());
    }
  }

  private Path createProbeJar(Path tempDir, String name, String classValue, String defaultValue) throws Exception {
    Path classesDir = tempDir.resolve(name + "-classes");
    Path sourceFile = classesDir.resolve(PROBE_CLASS_NAME.replace('.', File.separatorChar) + ".java");
    Path resourceFile = classesDir.resolve(METADATA_RESOURCE_NAME);

    Files.createDirectories(sourceFile.getParent());
    Files.createDirectories(resourceFile.getParent());
    Files.writeString(sourceFile, probeSource(classValue), UTF_8);
    Files.writeString(resourceFile, metadataResource(defaultValue), UTF_8);
    compileSource(classesDir, sourceFile);

    Path jarFile = tempDir.resolve(name + ".jar");
    createJar(classesDir, jarFile);
    return jarFile;
  }

  private String probeSource(String classValue) {
    return """
        package verification;

        public final class OverrideProbe {
          public static final String VALUE = "%s";

          private OverrideProbe() {
          }
        }
        """.formatted(classValue);
  }

  private String metadataResource(String defaultValue) {
    return """
        [
          {
            "key": "%s",
            "label": "Resource order verification",
            "valueType": "string",
            "defaultValue": "%s"
          }
        ]
        """.formatted(CONFIGURATION_KEY, defaultValue);
  }

  private void compileSource(Path outputDir, Path sourceFile) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Tests must run on a JDK so the probe class can be compiled");

    int exitCode = compiler.run(null, null, null,
        "-encoding", UTF_8.name(),
        "-d", outputDir.toString(),
        sourceFile.toString());
    assertEquals(0, exitCode);
  }

  private void createJar(Path classesDir, Path jarFile) throws Exception {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile));
         Stream<Path> paths = Files.walk(classesDir)) {
      for (Path file : paths.filter(Files::isRegularFile)
          .filter(file -> !file.toString().endsWith(".java"))
          .sorted()
          .toList()) {
        String entryName = classesDir.relativize(file).toString().replace(File.separatorChar, '/');
        jarOutputStream.putNextEntry(new JarEntry(entryName));
        Files.copy(file, jarOutputStream);
        jarOutputStream.closeEntry();
      }
    }
  }

  private List<ConfigurationMetadata> readMetadataInResourceOrder(List<URL> resourceUrls) throws Exception {
    List<ConfigurationMetadata> metadata = new ArrayList<>();
    for (URL resourceUrl : resourceUrls) {
      metadata.add(readSingleMetadata(resourceUrl));
    }
    return metadata;
  }

  private ConfigurationMetadata readSingleMetadata(URL resourceUrl) throws Exception {
    JarConfigurationMetadataProvider provider = new JarConfigurationMetadataProvider();
    List<ConfigurationMetadata> metadata = provider.parseMetadataResource(resourceUrl.toExternalForm(), readText(resourceUrl));
    assertEquals(1, metadata.size());
    return metadata.get(0);
  }

  private String readText(URL resourceUrl) throws Exception {
    try (InputStream inputStream = resourceUrl.openStream()) {
      return new String(inputStream.readAllBytes(), UTF_8);
    }
  }
}
