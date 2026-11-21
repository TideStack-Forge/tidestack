package com.ouroboros.dev.runtime.docker.backend;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import com.fasterxml.jackson.databind.*;
import com.github.dockerjava.core.*;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.ouroboros.container.service.impl.DockerContainerServiceImpl;
import com.ouroboros.dev.codeintelligence.language.*;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** Real Docker → registered runtime lifecycle → content transfer → Groovy semantic worker. */
@EnabledIfSystemProperty(named = "language.container.image", matches = ".+")
class DockerLanguageRuntimeIntegrationTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  @TempDir Path directory;
  @Test void managedContainerProvidesJavaCompletionsAndOutputContractsAndIsReclaimed() throws Exception {
    var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(System.getProperty("language.container.docker-host", "unix:///var/run/docker.sock")).build();
    try (var client = DockerClientImpl.getInstance(config, new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build())) {
      var containers = new DockerContainerServiceImpl(); ReflectionTestUtils.setField(containers, "dockerClient", client);
      var state = new InMemoryRuntimeStateStore();
      var events = new ArrayList<com.ouroboros.dev.runtime.event.DevRuntimeEvent>();
      var runtimes = new DefaultDevRuntimeService(List.of(new DockerLanguageRuntimeBackend(containers)), List.of(), state, events::add);
      Path classes = directory.resolve("extension"); Files.createDirectories(classes);
      Path source = classes.resolve("Customer.java");
      Files.writeString(source, "package extension; public class Customer { public String getName(){ throw new AssertionError(\"analysis must not execute\"); } }");
      assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
      Path jar = directory.resolve("extension.jar");
      try (var archive = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
        archive.putNextEntry(new java.util.jar.JarEntry("extension/Customer.class")); archive.write(Files.readAllBytes(classes.resolve("extension/Customer.class"))); archive.closeEntry();
      }
      var environment = new LanguageEnvironment("c".repeat(64), 21, List.of(jar));
      var deployment = new ManagedLanguageServerDeployment(runtimes, BackendType.DOCKER, Map.of("groovy", System.getProperty("language.container.image")), 1073741824L, 2000000000L);
      String containerId;
      var evidence = JSON.createObjectNode();
      try (var services = new EditorLanguageServices(List.of(new GroovyLanguageServerProvider(null)), directory.resolve("pool"), 1, Duration.ofMinutes(1), Clock.systemUTC(), deployment)) {
        var open = message("textDocument/didOpen"); open.withObject("params").putObject("textDocument").put("text", "def person = new extension.Customer()\nperson.").put("version", 1).put("languageId", "groovy");
        var completion = message("textDocument/completion").put("id", "completion"); var params = completion.withObject("params"); params.putObject("textDocument"); params.putObject("position").put("line", 1).put("character", 7);
        var results = services.exchange("groovy", environment, UUID.randomUUID().toString(), List.of(open, completion)).get(90, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(results.toString().contains("getName"), results.toString()); evidence.set("completion", JSON.valueToTree(results));
        var outputOpen = message("textDocument/didOpen"); outputOpen.withObject("params").putObject("textDocument").put("text", "return [customer: new extension.Customer()]").put("version", 1).put("languageId", "groovy");
        var context = message("ouroboros/context"); context.withObject("params").put("documentVersion", 1).putObject("snapshot").putArray("globals");
        var infer = message("ouroboros/inferOutput").put("id", "output"); infer.withObject("params").put("documentVersion", 1).put("draftRevision", 17).putObject("textDocument");
        var output = services.exchange("groovy", environment, UUID.randomUUID().toString(), List.of(outputOpen, context, infer)).get(60, java.util.concurrent.TimeUnit.SECONDS);
        var result = output.stream().filter(item -> item.has("id")).findFirst().orElseThrow(); assertFalse(result.has("error"), result.toString());
        assertEquals("string", result.at("/result/shape/schema/properties/customer/properties/name/type").asText()); evidence.set("output", result);
        var created = events.stream().filter(event -> event.getWorkloadId() != null).findFirst().orElseThrow(); assertNull(created.getAppName());
        String name = "ouroboros-language-" + created.getWorkloadId() + "-" + created.getProfile();
        var actual = client.inspectContainerCmd(name).exec(); containerId = actual.getId();
        assertEquals(1073741824L, actual.getHostConfig().getMemory()); assertEquals(2000000000L, actual.getHostConfig().getNanoCPUs());
        assertTrue(actual.getHostConfig().getAutoRemove());
        assertEquals("127.0.0.1", actual.getNetworkSettings().getPorts().getBindings().values().iterator().next()[0].getHostIp());
        assertTrue(actual.getMounts().isEmpty());
        evidence.put("containerId", containerId).put("memoryBytes", actual.getHostConfig().getMemory()).put("nanoCpus", actual.getHostConfig().getNanoCPUs());
        assertEquals(created.getWorkloadId(), runtimes.inspect(RuntimeIdentityKey.builder().workloadId(created.getWorkloadId()).profile(created.getProfile()).runtimeKind(RuntimeKind.LANGUAGE_SERVICE).backendType(BackendType.DOCKER).build()).get().getWorkloadId());
      }
      assertThrows(com.github.dockerjava.api.exception.NotFoundException.class, () -> client.inspectContainerCmd(containerId).exec());
      evidence.put("reclaimed", true);
      String report = System.getProperty("language.container.evidence"); if (report != null) { Path path = Path.of(report); Files.createDirectories(path.getParent()); JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), evidence); }
    }
  }
  private com.fasterxml.jackson.databind.node.ObjectNode message(String method) { var value = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method); value.putObject("params"); return value; }
}
