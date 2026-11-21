package com.ouroboros.uimodel.microweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;

class JarMicroWebModelProviderTest {

  @Test
  void failedDirectoryDoesNotHideHealthyDirectoryOrBecomePermanentSnapshot() {
    var broken = new AtomicBoolean(true);
    var provider = new JarMicroWebModelProvider(new ClasspathMetadataSource() {
      @Override
      public List<MetadataResource> findResourcesForOverride(String directory, Pattern pattern) {
        if (directory.contains("metadata/")) {
          return List.of(new MetadataResource("good.micro-web.json", "{\"name\":\"good\"}"));
        }
        if (broken.get()) {
          throw new IllegalStateException("jar unavailable");
        }
        return List.of(new MetadataResource("recovered.micro-web.json", "{\"name\":\"recovered\"}"));
      }
    });
    assertEquals("good", provider.getAllMicroWebModel().getFirst().getName());
    broken.set(false);
    assertEquals(2, provider.getAllMicroWebModel().size());
  }

  @Test
  void failedParseIsSkippedAndCanRetry() {
    var content = new AtomicReference<>("{");
    var provider = new JarMicroWebModelProvider(new ClasspathMetadataSource() {
      @Override
      public List<MetadataResource> findResourcesForOverride(String directory, Pattern pattern) {
        return List.of(new MetadataResource("test.micro-web.json", content.get()));
      }
    });
    assertTrue(provider.getAllMicroWebModel().isEmpty());
    content.set("{\"name\":\"recovered\",\"entry\":\"/entry\"}");

    assertEquals("recovered", provider.getAllMicroWebModel().getFirst().getName());
  }

  @Test
  void loadedModelsSurviveResourceFailureAndCallerMutation() {
    var unavailable = new AtomicBoolean();
    var source = new ClasspathMetadataSource() {
      @Override
      public List<MetadataResource> findResourcesForOverride(String directory, Pattern pattern) {
        if (unavailable.get()) {
          throw new IllegalStateException("classpath unavailable");
        }
        return List.of(new MetadataResource("test.micro-web.json",
            "{\"name\":\"test\",\"entry\":\"/original\"}"));
      }
    };
    var provider = new JarMicroWebModelProvider(source);
    var first = provider.getAllMicroWebModel();
    ((JarMicroWebModelProvider.JarMicroWebModel) first.getFirst()).setEntry("/modified");
    first.clear();
    unavailable.set(true);

    var second = provider.getAllMicroWebModel();

    assertEquals(1, second.size());
    assertEquals("/original", second.getFirst().getEntry());
  }

  @Test
  void shouldLoadTypedAndLegacyMetadataFromFixedDirectoriesAndIgnorePackageDirectory() {
    var provider = new JarMicroWebModelProvider();

    Map<String, MicroWebModel> models = provider.getAllMicroWebModel().stream()
        .collect(Collectors.toMap(MicroWebModel::getName, Function.identity()));

    assertEquals("/metadata-entry", models.get("test-micro").getEntry());
    assertEquals("/legacy-only-entry", models.get("legacy-only").getEntry());
    assertFalse(models.containsKey("package-json"));
  }

  @Test
  void shouldLoadPackageContentFromMetadataMirrorDirectoryFirst() throws Exception {
    var provider = new JarMicroWebModelProvider();
    var testMicro = provider.getAllMicroWebModel().stream()
        .filter(model -> "test-micro".equals(model.getName()))
        .findFirst()
        .orElseThrow();

    try (var packageContent = testMicro.getPackageContent()) {
      assertNotNull(packageContent);
      assertEquals("metadata package", new String(packageContent.readAllBytes(), StandardCharsets.UTF_8).trim());
    }
  }
}
