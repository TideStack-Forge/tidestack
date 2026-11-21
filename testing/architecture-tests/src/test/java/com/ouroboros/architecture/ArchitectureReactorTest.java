package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureReactorTest {
    @TempDir
    Path root;

    @Test
    void missingReactorOutputCannotProduceAPartialPassingScan() throws IOException {
        Path manifest = manifest("core/missing\tcore/missing/target/classes\ttrue");

        AssertionError failure = assertThrows(AssertionError.class,
            () -> ArchitectureReactor.read(root, manifest).importClasses());

        assertTrue(failure.getMessage().contains("core/missing"));
        assertTrue(failure.getMessage().contains("test-compile"));
    }

    @Test
    void resourceDirectoryDoesNotStandInForMissingCompiledCode() throws IOException {
        Files.createDirectories(root.resolve("core/empty/target/classes"));
        Path manifest = manifest("core/empty\tcore/empty/target/classes\ttrue");

        assertThrows(AssertionError.class,
            () -> ArchitectureReactor.read(root, manifest).importClasses());
    }

    @Test
    void undeclaredBuildOutputsDoNotChangeImportedClasses() throws IOException {
        compile("com.ouroboros.fixture.Included", "", "core/included");
        compile("com.ouroboros.fixture.Unrelated", "", "scratch/unrelated");
        Path manifest = manifest("core/included\tcore/included/target/classes\ttrue");

        var classes = ArchitectureReactor.read(root, manifest).importClasses();

        assertEquals(1, classes.size());
        assertTrue(classes.contain("com.ouroboros.fixture.Included"));
        assertFalse(classes.contain("com.ouroboros.fixture.Unrelated"));
    }

    @Test
    void outputsFromAnotherCheckoutAreRejected() throws IOException {
        Path manifest = root.resolve("reactor.tsv");
        Files.writeString(manifest, "repository\t" + root.resolve("another") + "\n");

        assertThrows(IllegalArgumentException.class,
            () -> ArchitectureReactor.read(root, manifest));
    }

    @Test
    void duplicateModuleIdentitiesAreRejected() throws IOException {
        Path manifest = manifest("core/a\tcore/a/target/classes\ttrue",
            "core/a\tcore/a/target/classes\ttrue");

        assertThrows(IllegalArgumentException.class,
            () -> ArchitectureReactor.read(root, manifest));
    }

    @Test
    void samePackagePrefixAcrossSeparateModulesDoesNotCreateACycle() throws IOException {
        compile("com.ouroboros.logicflow.Core", "", "core/flow-core");
        compile("com.ouroboros.mq.Api", "", "ability/mq-core");
        compile("com.ouroboros.logicflow.Development", "com.ouroboros.mq.Api dependency;",
            "core/flow-dev");
        compile("com.ouroboros.mq.Runtime", "com.ouroboros.logicflow.Core dependency;",
            "ability/mq-runtime");
        Path manifest = manifest(
            "core/flow-core\tcore/flow-core/target/classes\ttrue",
            "core/flow-dev\tcore/flow-dev/target/classes\ttrue",
            "ability/mq-core\tability/mq-core/target/classes\ttrue",
            "ability/mq-runtime\tability/mq-runtime/target/classes\ttrue");
        var reactor = ArchitectureReactor.read(root, manifest);
        var dependencies = ArchitectureModuleDependencies.between(reactor.importClasses(), reactor);

        assertEquals(Set.of(
            new ArchitectureModuleDependencies.Edge("core/flow-dev", "ability/mq-core"),
            new ArchitectureModuleDependencies.Edge("ability/mq-runtime", "core/flow-core")),
            dependencies);
        ArchitectureModuleDependencies.check(dependencies, Set.of());
    }

    @Test
    void compiledReferencesInBothDirectionsAreReportedAsAModuleCycle() throws IOException {
        compile("com.ouroboros.mq.Peer", "", "ability/peer");
        compile("com.ouroboros.logicflow.Source", "com.ouroboros.mq.Peer dependency;", "core/source");
        compile("com.ouroboros.mq.Peer", "com.ouroboros.logicflow.Source dependency;", "ability/peer");
        Path manifest = manifest("core/source\tcore/source/target/classes\ttrue",
            "ability/peer\tability/peer/target/classes\ttrue");
        var reactor = ArchitectureReactor.read(root, manifest);

        AssertionError failure = assertThrows(AssertionError.class,
            () -> ArchitectureModuleDependencies.check(
                ArchitectureModuleDependencies.between(reactor.importClasses(), reactor), Set.of()));

        assertTrue(failure.getMessage().contains("core/source -> ability/peer"));
        assertTrue(failure.getMessage().contains("ability/peer -> core/source"));
    }

    @Test
    void resourceOnlyModulesDoNotRequireJavaClassFiles() throws IOException {
        Path output = root.resolve("metadata/target/classes");
        Files.createDirectories(output);
        Files.writeString(output.resolve("fixture.json"), "{}");
        Path manifest = manifest("metadata\tmetadata/target/classes\tfalse");

        assertEquals(0, ArchitectureReactor.read(root, manifest).importClasses().size());
    }

    @Test
    void moduleOutputsCannotEscapeTheCheckout() throws IOException {
        Path manifest = manifest("core/outside\t../outside/target/classes\ttrue");

        assertThrows(IllegalArgumentException.class,
            () -> ArchitectureReactor.read(root, manifest));
    }

    private Path manifest(String... modules) throws IOException {
        Path manifest = root.resolve("reactor.tsv");
        Files.writeString(manifest,
            "repository\t" + root + "\n" + String.join("\n", modules) + "\n");
        return manifest;
    }

    private void compile(String name, String fields, String module) throws IOException {
        Path source = root.resolve("sources/" + name.replace('.', '/') + ".java");
        Files.createDirectories(source.getParent());
        int separator = name.lastIndexOf('.');
        Files.writeString(source, "package " + name.substring(0, separator)
            + "; public class " + name.substring(separator + 1) + " { " + fields + " }");
        Path output = root.resolve(module + "/target/classes");
        Files.createDirectories(output);
        String classpath;
        try (var paths = Files.walk(root)) {
            classpath = String.join(java.io.File.pathSeparator,
                paths.filter(path -> path.endsWith("target/classes"))
                    .map(Path::toString).toList());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-classpath", classpath, "-d", output.toString(), source.toString()));
    }
}
