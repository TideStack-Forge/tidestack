package com.ouroboros.resource.manage.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceMetadata;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.provider.ResourceOverrideProvider;
import com.ouroboros.resource.provider.ResourceProvider;
import com.ouroboros.resource.runtime.config.ResourceAutoConfiguration;
import com.ouroboros.util.Json;

class ResourceProviderWiringTest {
    @TempDir Path root;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ResourceAutoConfiguration.class))
        .withBean(RuntimeResourceManageService.class);

    @Test
    void defaultAssemblyUsesTheSameOverrideForManagementAndResolution() throws Exception {
        Path defaults = root.resolve("defaults");
        Files.createDirectories(defaults.resolve("frontend"));
        Files.writeString(defaults.resolve("frontend/example.txt"), "default");

        runner.withPropertyValues(
            "ouroboros.resource.app-default-base-directory=" + defaults,
            "ouroboros.resource.runtime-override-base-directory=" + root.resolve("overrides"))
            .run(context -> {
                assertNull(context.getStartupFailure());
                exerciseOverrideAndRestore(context.getBean(RuntimeResourceManageService.class));
                assertFalse(Files.exists(root.resolve("overrides/frontend/example.txt")));
                assertFalse(Files.exists(root.resolve("overrides/frontend/example.txt.resource.json")));
            });
    }

    @Test
    void coreInterfaceBeansReplaceDefaultImplementationsWithoutChangingTheCallPath() {
        ResourceProvider defaults = new MemoryProvider(Map.of("frontend/example.txt", "default".getBytes(StandardCharsets.UTF_8)));
        var overrides = new MemoryOverrideProvider();

        runner.withBean("appDefaultResourceProvider", ResourceProvider.class, () -> defaults)
            .withBean("runtimeOverrideResourceProvider", ResourceOverrideProvider.class, () -> overrides)
            // A custom provider must not require the default storage backend to exist.
            .withPropertyValues("ouroboros.resource.runtime-override.storage.type=not-installed")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertSame(overrides, context.getBean(ResourceOverrideProvider.class));
                exerciseOverrideAndRestore(context.getBean(RuntimeResourceManageService.class));
                assertEquals(1, overrides.writes);
                assertEquals(1, overrides.deletes);
            });
    }

    private void exerciseOverrideAndRestore(RuntimeResourceManageService service) {
        assertArrayEquals("default".getBytes(StandardCharsets.UTF_8),
            service.downloadEffectiveContent(ResourceScope.FRONTEND, "example.txt"));
        var item = service.upload(ResourceScope.FRONTEND, "example.txt",
            "override".getBytes(StandardCharsets.UTF_8), "text/plain", "example.txt", "tester");
        assertEquals(ResourceLayer.RUNTIME_OVERRIDE, item.getEffectiveLayer());
        assertTrue(item.isDefaultExists());
        assertTrue(item.isHasOverride());
        assertArrayEquals("override".getBytes(StandardCharsets.UTF_8),
            service.downloadEffectiveContent(ResourceScope.FRONTEND, "example.txt"));
        assertEquals(1, service.list(ResourceScope.FRONTEND, "example").size());
        assertEquals(ResourceLayer.APP_DEFAULT,
            service.restoreDefault(ResourceScope.FRONTEND, "example.txt").getEffectiveLayer());
        assertArrayEquals("default".getBytes(StandardCharsets.UTF_8),
            service.downloadEffectiveContent(ResourceScope.FRONTEND, "example.txt"));
    }

    private static class MemoryProvider implements ResourceProvider {
        final Map<String, byte[]> content = new LinkedHashMap<>();
        MemoryProvider(Map<String, byte[]> initial) { content.putAll(initial); }
        @Override public Optional<Resource> getResource(String path) {
            return Optional.ofNullable(content.get(path)).map(bytes -> Resource.builder()
                .path(path).content(new ByteArrayInputStream(bytes)).build());
        }
        @Override public boolean exists(String path) { return content.containsKey(path); }
        @Override public List<String> listResources(String directory) {
            return content.keySet().stream().filter(path -> path.startsWith(directory + "/")).toList();
        }
    }

    private static final class MemoryOverrideProvider extends MemoryProvider implements ResourceOverrideProvider {
        private final Map<String, ResourceMetadata> metadata = new LinkedHashMap<>();
        int writes;
        int deletes;
        MemoryOverrideProvider() { super(Map.of()); }
        @Override public Optional<Resource> getResource(String path) {
            return Optional.ofNullable(content.get(path)).map(bytes -> Resource.builder()
                .descriptor(metadata.get(path).toDescriptor()).content(new ByteArrayInputStream(bytes)).build());
        }
        @Override public void writeOverride(String path, byte[] bytes, byte[] serializedMetadata, String contentType) {
            content.put(path, bytes.clone());
            metadata.put(path, Json.toBean(ResourceMetadata.class, new ByteArrayInputStream(serializedMetadata)));
            writes++;
        }
        @Override public void deleteOverride(String path) {
            content.remove(path);
            metadata.remove(path);
            deletes++;
        }
    }
}
