package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureLayerSupportTest {
    @TempDir Path root;
    private final List<String> modules = List.of("libs/base", "core/engine", "security/auth",
        "core/ouroboros-app-module-model/ouroboros-app-module-model-core", "ability/service", "app-module/app", "dev-module/dev", "platform/host");
    private final List<String> types = List.of("Base", "Engine", "Security", "Projection", "Capability", "App", "Dev", "Host");

    @Test
    void coversNewPackagesAndAcceptsDependenciesTowardInnerPolicies() throws Exception {
        var reactor = fixture("", "sample.Base base;", "sample.Security security;");
        assertFalse(ArchitectureLayerSupport.rule(reactor).evaluate(reactor.importClasses()).hasViolation());
    }

    @Test
    void foundationCannotDependOnCore() throws Exception {
        var reactor = fixture("sample.Engine engine;", "", "");
        var result = ArchitectureLayerSupport.rule(reactor).evaluate(reactor.importClasses());
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("sample.Base.engine"));
    }

    @Test
    void coreCannotDependOnSecurityOrTheProjectionBand() throws Exception {
        var reactor = fixture("", "sample.Security security; sample.Projection projection;", "");
        var result = ArchitectureLayerSupport.rule(reactor).evaluate(reactor.importClasses());
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("sample.Engine.security"));
        assertTrue(result.getFailureReport().toString().contains("sample.Engine.projection"));
    }

    private ArchitectureReactor fixture(String foundation, String core, String bridge) throws Exception {
        Path sources = root.resolve("sources");
        Files.createDirectories(sources);
        // First compile all peers; then replace each class with the final fields in its own module.
        for (int i = 0; i < types.size(); i++) compile(sources, i, "");
        compile(sources, 0, foundation);
        compile(sources, 1, core);
        compile(sources, 3, bridge);
        compile(sources, 4, "sample.Engine engine; sample.Security security; sample.Projection projection;");
        compile(sources, 5, "sample.Capability capability;");
        compile(sources, 6, "sample.App app;");
        compile(sources, 7, "sample.Dev dev;");
        Path manifest = root.resolve("reactor.tsv");
        Files.writeString(manifest, "repository\t" + root + "\n" + String.join("\n", modules.stream()
            .map(module -> module + "\t" + module + "/target/classes\ttrue").toList()) + "\n");
        return ArchitectureReactor.read(root, manifest);
    }

    private void compile(Path sources, int index, String fields) throws Exception {
        Path source = sources.resolve(types.get(index) + ".java");
        Files.writeString(source, "package sample; public class " + types.get(index) + " { " + fields + " }");
        Path output = root.resolve(modules.get(index) + "/target/classes");
        Files.createDirectories(output);
        String classpath = String.join(java.io.File.pathSeparator,
            modules.stream().map(module -> root.resolve(module + "/target/classes").toString()).toList());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-classpath", classpath, "-d", output.toString(), source.toString()));
    }
}
