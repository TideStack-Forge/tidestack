package com.ouroboros.uischema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUiSchemaSchemaProviderTest {

  @Test
  void shouldLoadSchemaFilesAndExposeThemThroughProxy() throws Exception {
    Path home = Files.createTempDirectory("ui-schema-provider-test");
    writeSchema(home.resolve("ui-schema/runtime-page.json"), "{\"name\":\"Runtime.Page\",\"title\":\"Runtime\",\"schema\":{\"type\":\"page\"}}");

    AppUiSchemaSchemaProvider provider = new AppUiSchemaSchemaProvider(new StaticAppContext(home.toString()));
    provider.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));

    Map<String, Object> schemaMap = new AppUiSchemaSchemaProvider.Proxy().getUISchemaMap();
    Object schema = new AppUiSchemaSchemaProvider.Proxy().getUISchema("/runtime/page").orElse(null);

    assertEquals(1, schemaMap.size());
    assertTrue(schemaMap.containsKey("/runtime/page"));
    assertNotNull(schema);
    assertEquals(schemaMap.get("/runtime/page"), schema);

    deleteRecursively(home);
  }

  @Test
  void shouldPreferTypedSchemaWhenLegacyWrapperHasSameName() throws Exception {
    Path home = Files.createTempDirectory("ui-schema-provider-override-test");
    writeSchema(home.resolve("ui-schema/runtime-page.json"), "{\"name\":\"Runtime.Page\",\"schema\":{\"type\":\"page\",\"title\":\"legacy\"}}");
    writeSchema(home.resolve("ui-schema/runtime-page.ui-schema.json"), "{\"name\":\"Runtime.Page\",\"schema\":{\"type\":\"page\",\"title\":\"typed\"}}");

    AppUiSchemaSchemaProvider provider = new AppUiSchemaSchemaProvider(new StaticAppContext(home.toString()));
    provider.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));

    Object schema = new AppUiSchemaSchemaProvider.Proxy().getUISchema("/runtime/page").orElseThrow();

    assertEquals("typed", schema.getClass().getMethod("getProp", String.class).invoke(schema, "title"));

    deleteRecursively(home);
  }

  @Test
  void shouldExposeEmptyMapWhenUiSchemaDirectoryIsMissing() throws Exception {
    Path home = Files.createTempDirectory("ui-schema-provider-empty-test");

    new AppUiSchemaSchemaProvider(new StaticAppContext(home.toString()));

    Map<String, Object> schemaMap = new AppUiSchemaSchemaProvider.Proxy().getUISchemaMap();

    assertTrue(schemaMap.isEmpty());

    deleteRecursively(home);
  }

  private static void writeSchema(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.deleteIfExists(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static final class StaticAppContext implements com.ouroboros.AppContext {
    private final String homePath;

    private StaticAppContext(String homePath) {
      this.homePath = homePath;
    }

    @Override
    public String getHomePath() {
      return homePath;
    }
  }
}
