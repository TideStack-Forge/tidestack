package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class ArchitectureSpiSupportTest {
    private static final String SERVICE = "com.ouroboros.logicflow.nodes.NodeBuilder";
    @TempDir Path temporary;

    @Test
    void acceptsARegisteredConcreteProvider() throws Exception {
        Path output = compile("public Provider() {}", "implements NodeBuilder");
        register(output, "com.ouroboros.logicflow.nodes.Provider # comment\n");
        assertTrue(violations(output).isEmpty());
    }

    @Test
    void rejectsAnUnregisteredNodeBuilderEvenWhenItsWholeDescriptorDisappears() throws Exception {
        Path output = compile("public Provider() {}", "implements NodeBuilder");
        assertTrue(violations(output).stream().anyMatch(message -> message.contains("Provider is not registered")));
    }

    @Test
    void temporaryCheckoutDescriptorsCannotRegisterAProductionProvider() throws Exception {
        Path output = compile("public Provider() {}", "implements NodeBuilder");
        register(temporary.resolve("tmp/old-checkout/target/classes"), "com.ouroboros.logicflow.nodes.Provider");
        assertTrue(violations(output).stream().anyMatch(message -> message.contains("Provider is not registered")));
    }

    @Test
    void rejectsAProviderForTheWrongContract() throws Exception {
        Path output = compile("public Provider() {}", "");
        register(output, "com.ouroboros.logicflow.nodes.Provider");
        assertTrue(violations(output).stream().anyMatch(message -> message.contains("is not assignable")));
    }

    @Test
    void rejectsAProviderWithoutAPublicNoArgumentConstructor() throws Exception {
        Path output = compile("public Provider(String value) {}", "implements NodeBuilder");
        register(output, "com.ouroboros.logicflow.nodes.Provider");
        assertTrue(violations(output).stream().anyMatch(message -> message.contains("no-argument constructor")));
    }

    @Test
    void rejectsMissingProvidersAndUnresolvableContracts() throws Exception {
        Path output = compile("public Provider() {}", "implements NodeBuilder");
        register(output, "com.ouroboros.logicflow.nodes.Missing");
        assertTrue(violations(output).stream().anyMatch(message -> message.contains("missing provider")));
        Path badContract = output.resolve("META-INF/services/example.MissingContract");
        Files.writeString(badContract, "com.ouroboros.logicflow.nodes.Provider");
        assertTrue(violations(output).stream().anyMatch(message -> message.contains("cannot resolve provider contract")));
    }

    private List<String> violations(Path output) throws Exception {
        try (var loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            return ArchitectureSpiSupport.violations(new ClassFileImporter().importPath(output), List.of(output), loader);
        }
    }

    private Path compile(String constructor, String contract) throws Exception {
        Path source = temporary.resolve("src/com/ouroboros/logicflow/nodes");
        Files.createDirectories(source);
        Path service = source.resolve("NodeBuilder.java");
        Path provider = source.resolve("Provider.java");
        Files.writeString(service, "package com.ouroboros.logicflow.nodes; public interface NodeBuilder {}");
        Files.writeString(provider, "package com.ouroboros.logicflow.nodes; public class Provider " + contract
            + " { " + constructor + " }");
        Path output = temporary.resolve("production/target/classes");
        Files.createDirectories(output);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-d", output.toString(), service.toString(), provider.toString());
        if (result != 0) throw new IllegalStateException("Cannot compile SPI fixture: " + result);
        return output;
    }

    private void register(Path output, String providers) throws Exception {
        Path descriptor = output.resolve("META-INF/services/" + SERVICE);
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, providers);
    }
}
