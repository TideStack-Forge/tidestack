package com.ouroboros.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class LogicFlowExtensionContractTest {

  @Test
  void extensionBundleClassificationManifestShouldMatchCurrentBundles() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Map<String, BundleClassification> bundles = readBundleClassificationManifest(repoRoot);

    assertTrue(bundles.containsKey("official-builtin"), "Missing official builtin bundle classification");
    assertTrue(bundles.containsKey("data-model"), "Missing data-model bundle classification");
    assertTrue(bundles.containsKey("extra-nodes"), "Missing extra-nodes bundle classification");
    assertTrue(bundles.containsKey("external-fixture"), "Missing external fixture bundle classification");

    BundleClassification officialBundle = bundles.get("official-builtin");
    assertEquals("public", officialBundle.targetVisibility);
    assertEquals("include_in_public_repo", officialBundle.exportPolicy);
    assertTrue(officialBundle.hasNodeType("script"), "ScriptNode must stay in the official public bundle");
    assertTrue(officialBundle.hasRequiredCapability("script"), "ScriptNode must be a required capability");
    assertSourceBundleMatchesClassification(repoRoot, officialBundle);

    BundleClassification dataModelBundle = bundles.get("data-model");
    assertEquals("public", dataModelBundle.targetVisibility);
    assertEquals("include_in_public_repo", dataModelBundle.exportPolicy);
    assertSourceBundleMatchesClassification(repoRoot, dataModelBundle);

    BundleClassification extraNodesBundle = bundles.get("extra-nodes");
    assertEquals("internal-adapter", extraNodesBundle.targetVisibility);
    assertEquals("exclude_by_default", extraNodesBundle.exportPolicy);
    assertSourceBundleMatchesClassification(repoRoot, extraNodesBundle);

    Set<String> publicTypes = officialBundle.nodeTypes();
    publicTypes.addAll(dataModelBundle.nodeTypes());
    Set<String> internalTypes = new HashSet<String>();
    internalTypes.addAll(extraNodesBundle.nodeTypes());
    for (String internalType : internalTypes) {
      assertFalse(publicTypes.contains(internalType),
          () -> "Internal node type must not overlap official public type: " + internalType);
    }
  }

  @Test
  void externalBundleContractFixtureShouldUseSpiAndNodeDefineJson() throws IOException {
    Path repoRoot = resolveRepoRoot();
    BundleClassification externalBundle = readBundleClassificationManifest(repoRoot).get("external-fixture");
    assertEquals("test-only", externalBundle.targetVisibility);
    assertEquals("not_exported", externalBundle.exportPolicy);
    assertSourceBundleMatchesClassification(repoRoot, externalBundle);

    Path nodeFactoryPath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/NodeFactory.java");
    String nodeFactorySource = Files.readString(nodeFactoryPath, StandardCharsets.UTF_8);
    for (NodeClassification node : externalBundle.nodes) {
      assertFalse(nodeFactorySource.contains(node.builderProvider.replace('$', '.')),
          () -> "NodeFactory must not hardcode external fixture builder " + node.builderProvider);
    }
  }

  @Test
  void logicFlowFormalSpiDescriptorsShouldRegisterDeclaredProviders() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Map<String, String> requiredDescriptors = new LinkedHashMap<String, String>();
    requiredDescriptors.put(
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/resources/META-INF/services/com.ouroboros.logicflow.model.FlowModelMetaProvider",
        "com.ouroboros.logicflow.AppLogicModelMetaProvider$SingletonHolder");
    requiredDescriptors.put(
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/resources/META-INF/services/com.ouroboros.logicflow.model.FlowModelProvider",
        "com.ouroboros.logicflow.DefaultLogicFlowProvider$SingletonHolder");
    requiredDescriptors.put(
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine",
        "com.ouroboros.logicflow.script.CommonScriptLogicFlowScriptEngine");
    requiredDescriptors.put(
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper/src/main/resources/META-INF/services/com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer",
        "com.ouroboros.logicflow.debug.LogicFlowDebugRuntimeAnalyzer");
    requiredDescriptors.put(
        "core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/resources/META-INF/services/com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider",
        "com.ouroboros.logicflow.authoring.provider.JarLogicFlowNodeDefineProvider");

    for (Map.Entry<String, String> entry : requiredDescriptors.entrySet()) {
      Path descriptorPath = repoRoot.resolve(entry.getKey());
      assertTrue(Files.exists(descriptorPath), () -> "Missing descriptor: " + descriptorPath);

      List<String> providers = readProviderEntries(descriptorPath);
      assertTrue(providers.contains(entry.getValue()),
          () -> "Descriptor " + descriptorPath + " should contain " + entry.getValue() + " but was " + providers);
    }
  }

  @Test
  void nodeBuilderExtensionModulesShouldShipNodeDefineBundles() throws IOException {
    Path repoRoot = resolveRepoRoot();
    List<Path> descriptorPaths = findSourceFiles(repoRoot,
        "META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder");

    assertFalse(descriptorPaths.isEmpty(), "Expected at least one NodeBuilder descriptor");

    for (Path descriptorPath : descriptorPaths) {
      if (descriptorPath.toString().contains("/ouroboros-logicflow-builtin-nodes/")) {
        continue;
      }
      Path moduleResourcesDir = descriptorPath.getParent().getParent().getParent();
      Path modulePath = moduleResourcesDir.getParent().getParent().getParent();
      Path nodeDefineDir = modulePath.resolve("src/main/metadata");

      assertTrue(Files.isDirectory(nodeDefineDir),
          () -> "Missing node-define directory for " + descriptorPath);

      try (Stream<Path> pathStream = Files.list(nodeDefineDir)) {
        List<Path> jsonFiles = pathStream
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(".json"))
            .collect(Collectors.toList());
        assertFalse(jsonFiles.isEmpty(),
            () -> "Missing node-define bundle json files for " + descriptorPath);
      }
    }
  }

  @Test
  void nodeFactoryShouldNotReferenceBuildersDeclaredByExternalModules() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Path nodeFactoryPath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes/NodeFactory.java");
    String nodeFactorySource = Files.readString(nodeFactoryPath, StandardCharsets.UTF_8);

    for (Path descriptorPath : findSourceFiles(repoRoot, "META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder")) {
      List<String> providers = readProviderEntries(descriptorPath);
      for (String provider : providers) {
        String sourceStyleName = provider.replace('$', '.');
        assertFalse(nodeFactorySource.contains(sourceStyleName),
            () -> "NodeFactory should not directly reference external builder " + sourceStyleName);
      }
    }
  }

  @Test
  void officialNodeDefinesShouldPairWithOfficialBuilders() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Map<String, String> expectedNodeDefineToBuilder = new LinkedHashMap<String, String>();
    expectedNodeDefineToBuilder.put("CallFlowNode.flow-node.json", "CallFlowNode.java");
    expectedNodeDefineToBuilder.put("ConcatNode.flow-node.json", "ConcatNode.java");
    expectedNodeDefineToBuilder.put("ConstNode.flow-node.json", "ConstNode.java");
    expectedNodeDefineToBuilder.put("DistinctNode.flow-node.json", "DistinctNode.java");
    expectedNodeDefineToBuilder.put("FilterNode.flow-node.json", "FilterNode.java");
    expectedNodeDefineToBuilder.put("IntersectNode.flow-node.json", "IntersectNode.java");
    expectedNodeDefineToBuilder.put("MappingNode.flow-node.json", "MappingNode.java");
    expectedNodeDefineToBuilder.put("MergeNode.flow-node.json", "MergeNode.java");
    expectedNodeDefineToBuilder.put("MutexNode.flow-node.json", "MutexNode.java");
    expectedNodeDefineToBuilder.put("OmitNode.flow-node.json", "OmitNode.java");
    expectedNodeDefineToBuilder.put("PickNode.flow-node.json", "PickNode.java");
    expectedNodeDefineToBuilder.put("ReturnNode.flow-node.json", "ReturnNode.java");
    expectedNodeDefineToBuilder.put("ScriptNode.flow-node.json", "ScriptNode.java");
    expectedNodeDefineToBuilder.put("SubstractNode.flow-node.json", "SubtractNode.java");
    expectedNodeDefineToBuilder.put("SwitchNode.flow-node.json", "SwitchNode.java");
    expectedNodeDefineToBuilder.put("TakeNode.flow-node.json", "TakeNode.java");
    expectedNodeDefineToBuilder.put("ValidateNode.flow-node.json", "ValidateNode.java");

    Path nodeDefineDir = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata");
    assertTrue(Files.isDirectory(nodeDefineDir), () -> "Missing official authoring node define directory: " + nodeDefineDir);

    Set<String> actualNodeDefineFiles;
    try (Stream<Path> pathStream = Files.list(nodeDefineDir)) {
      actualNodeDefineFiles = pathStream
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(fileName -> fileName.endsWith(".json"))
          .collect(Collectors.toSet());
    }
    assertTrue(actualNodeDefineFiles.containsAll(expectedNodeDefineToBuilder.keySet()),
        () -> "Official authoring node defines should include " + expectedNodeDefineToBuilder.keySet()
            + " but were " + actualNodeDefineFiles);
    assertTrue(expectedNodeDefineToBuilder.keySet().containsAll(actualNodeDefineFiles),
        () -> "Official authoring node defines should not include unpaired files: " + actualNodeDefineFiles);

    Set<String> nodeTypes = new HashSet<String>();
    Path descriptorPath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder");
    assertTrue(Files.exists(descriptorPath), () -> "Missing official builtin builder descriptor: " + descriptorPath);
    List<String> officialBuilderProviders = readProviderEntries(descriptorPath);

    for (Map.Entry<String, String> entry : expectedNodeDefineToBuilder.entrySet()) {
      Path nodeDefinePath = nodeDefineDir.resolve(entry.getKey());
      String jsonSource = Files.readString(nodeDefinePath, StandardCharsets.UTF_8);
      String nodeType = extractJsonString(jsonSource, "type", nodeDefinePath);
      assertTrue(nodeTypes.add(nodeType), () -> "Duplicate official node define type: " + nodeType);

      assertOfficialBuilderSourceExists(repoRoot, entry.getValue());
      String providerName = "com.ouroboros.logicflow.nodes."
          + entry.getValue().replace(".java", "$Builder");
      assertTrue(officialBuilderProviders.contains(providerName),
          () -> "Official builtin descriptor should contain " + providerName
              + " but was " + officialBuilderProviders);
    }
  }

  @Test
  void coreShouldNotOwnOfficialNodeImplementationsOrNodeDefineResources() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Path coreNodeDir = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/nodes");
    List<String> forbiddenNodeSources = List.of(
        "CallFlowNode.java",
        "ConcatNode.java",
        "ConstNode.java",
        "DistinctNode.java",
        "FilterNode.java",
        "IntersectNode.java",
        "MappingNode.java",
        "MergeNode.java",
        "MutexNode.java",
        "OmitNode.java",
        "PickNode.java",
        "ReturnNode.java",
        "ScriptNode.java",
        "SubtractNode.java",
        "SwitchNode.java",
        "TakeNode.java",
        "ValidateNode.java"
    );

    for (String sourceFile : forbiddenNodeSources) {
      assertFalse(Files.exists(coreNodeDir.resolve(sourceFile)),
          () -> "Core must not own official concrete node implementation: " + sourceFile);
    }

    Path coreNodeDefineDir = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/resources/META-INF/ouroboros/logic-flow-node-define");
    assertFalse(Files.exists(coreNodeDefineDir),
        () -> "Core must not own official authoring NodeDefine resources: " + coreNodeDefineDir);
  }

  @Test
  void coreShouldNotImportPlatformAdapters() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Path coreMainJavaDir = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java");
    List<String> forbiddenImportPrefixes = List.of(
        "import com.ouroboros.data.",
        "import com.ouroboros.dev.",
        "import com.ouroboros.web.",
        "import com.ouroboros.security.",
        "import com.ouroboros.websocket.",
        "import com.ouroboros.message."
    );

    try (Stream<Path> pathStream = Files.walk(coreMainJavaDir)) {
      List<Path> javaSources = pathStream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .collect(Collectors.toList());
      for (Path javaSource : javaSources) {
        String source = Files.readString(javaSource, StandardCharsets.UTF_8);
        for (String forbiddenImportPrefix : forbiddenImportPrefixes) {
          assertFalse(source.contains(forbiddenImportPrefix),
              () -> "LogicFlow core must not import platform adapter package "
                  + forbiddenImportPrefix + " in " + javaSource);
        }
      }
    }
  }

  @Test
  void publicCandidatesShouldNotImportInternalLogicFlowAdaptersOrPlatformPackages() throws IOException {
    Path repoRoot = resolveRepoRoot();
    List<String> publicCandidateSourceRoots = List.of(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java",
        "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java",
        "core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/java"
    );
    List<String> forbiddenImports = List.of(
        "import com.ouroboros.logicflow.adapter.",
        "import com.ouroboros.logicflow.dev.",
        "import com.ouroboros.logicflow.debug.",
        "import com.ouroboros.logicflow.DefaultLogicFlowExecutionFacade",
        "import com.ouroboros.logicflow.LogicFlowExecutionFacade",
        "import com.ouroboros.data.",
        "import com.ouroboros.dev.",
        "import com.ouroboros.web.",
        "import com.ouroboros.security.",
        "import com.ouroboros.websocket.",
        "import com.ouroboros.mq.",
        "import com.ouroboros.message.",
        "import com.ouroboros.modelmanage.",
        "import com.ouroboros.vcs.",
        "import com.ouroboros.exception."
    );

    for (String publicCandidateSourceRoot : publicCandidateSourceRoots) {
      Path sourceRoot = repoRoot.resolve(publicCandidateSourceRoot);
      assertTrue(Files.isDirectory(sourceRoot), () -> "Missing public candidate source root: " + sourceRoot);
      assertJavaSourcesDoNotContain(sourceRoot, forbiddenImports,
          "Public LogicFlow candidate must not import internal adapter or platform package");
    }
  }

  @Test
  void publicCandidatePomsShouldNotDependOnInternalLogicFlowAdaptersOrPlatformArtifacts() throws IOException {
    Path repoRoot = resolveRepoRoot();
    List<String> publicCandidatePoms = List.of(
        "core/ouroboros-logicflow/ouroboros-logicflow-core/pom.xml",
        "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/pom.xml",
        "core/ouroboros-logicflow/ouroboros-logicflow-authoring/pom.xml"
    );
    List<String> forbiddenArtifactIds = List.of(
        "<artifactId>ouroboros-logicflow-data-model</artifactId>",
        "<artifactId>ouroboros-logicflow-extra-nodes</artifactId>",
        "<artifactId>ouroboros-logicflow-runtime</artifactId>",
        "<artifactId>ouroboros-logicflow-dev</artifactId>",
        "<artifactId>ouroboros-logicflow-web-adapter</artifactId>",
        "<artifactId>ouroboros-logicflow-all</artifactId>",
        "<artifactId>ouroboros-data-core</artifactId>",
        "<artifactId>ouroboros-dev-model-manage-webapi-scaffold</artifactId>",
        "<artifactId>ouroboros-message-queue-core</artifactId>",
        "<artifactId>ouroboros-web-security-core</artifactId>",
        "<artifactId>ouroboros-websocket-core</artifactId>",
        "<artifactId>common-app</artifactId>",
        "<artifactId>common-dev</artifactId>",
        "<artifactId>common-web</artifactId>",
        "<artifactId>common-exception</artifactId>",
        "<artifactId>ouroboros-util-excel</artifactId>"
    );

    for (String publicCandidatePom : publicCandidatePoms) {
      Path pomPath = repoRoot.resolve(publicCandidatePom);
      assertTrue(Files.exists(pomPath), () -> "Missing public candidate POM: " + pomPath);
      String pomSource = Files.readString(pomPath, StandardCharsets.UTF_8);
      for (String forbiddenArtifactId : forbiddenArtifactIds) {
        assertFalse(pomSource.contains(forbiddenArtifactId),
            () -> "Public LogicFlow candidate POM must not depend on internal adapter/platform artifact "
                + forbiddenArtifactId + " in " + pomPath);
      }
    }
  }

  @Test
  void scriptNodeShouldDependOnScriptEngineContractNotConcreteEngines() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Path builtinMainJavaDir = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java");
    assertJavaSourcesDoNotContain(builtinMainJavaDir, List.of(
        "import groovy.",
        "import org.graalvm.polyglot.",
        "import com.ouroboros.script."
    ), "Builtin ScriptNode must depend on LogicFlowScriptEngine, not a concrete script engine");

    assertFileDoesNotContain(repoRoot,
        "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/pom.xml",
        "<artifactId>groovy</artifactId>",
        "<artifactId>js</artifactId>",
        "<artifactId>common-script</artifactId>");

    Path scriptEnginePath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/script/LogicFlowScriptEngine.java");
    assertTrue(Files.readString(scriptEnginePath, StandardCharsets.UTF_8).contains("List<String> getSupportedLanguages()"),
        "LogicFlowScriptEngine should let engines declare supported languages");
  }

  @Test
  void debugHelperShouldStayOutOfRuntimeAllAndDefaultImageInputs() throws IOException {
    Path repoRoot = resolveRepoRoot();

    assertFalse(Files.exists(repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/debug")),
        "LogicFlow runtime must not own debug helper implementation classes");
    assertFalse(Files.exists(repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/resources/META-INF/services/com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer")),
        "LogicFlow runtime must not own debug analyzer SPI descriptor");

    assertFileDoesNotContain(repoRoot,
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/pom.xml",
        "<artifactId>ouroboros-logicflow-debug-helper</artifactId>");
    assertFileDoesNotContain(repoRoot,
        "ability/ouroboros-ability-all/ouroboros-ability-all-runtime/pom.xml",
        "<artifactId>ouroboros-logicflow-debug-helper</artifactId>");
    assertFileDoesNotContain(repoRoot,
        "platform/ouroboros-mothership/pom.xml",
        "<artifactId>ouroboros-logicflow-debug-helper</artifactId>");
    // The POM is the Maven dependency contract; test fixtures are outside the image/runtime graph.
    var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    org.w3c.dom.Document pom;
    try {
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      pom = factory.newDocumentBuilder().parse(repoRoot.resolve("platform/ouroboros-develop/pom.xml").toFile());
    } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException invalid) {
      throw new IOException("Cannot read the development platform dependency contract", invalid);
    }
    var dependencies = pom.getElementsByTagName("dependency");
    for (int index = 0; index < dependencies.getLength(); index++) {
      var dependency = (org.w3c.dom.Element) dependencies.item(index);
      var artifacts = dependency.getElementsByTagName("artifactId");
      if (artifacts.getLength() > 0 && "ouroboros-logicflow-debug-helper".equals(artifacts.item(0).getTextContent().trim())) {
        var scopes = dependency.getElementsByTagName("scope");
        assertEquals("test", scopes.getLength() == 0 ? "compile" : scopes.item(0).getTextContent().trim(),
            "Debug helper must stay outside the development platform production dependency graph");
      }
    }
  }

  @Test
  void developImageBuildShouldPackageDebugHelperAsSupportArtifact() throws IOException {
    Path repoRoot = resolveRepoRoot();

    assertFileContains(repoRoot,
        "build.sh",
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper",
        "ouroboros-logicflow-debug-helper-${version}.jar",
        "${develop_support_path}/ouroboros-logicflow-debug-helper.jar");
    assertFileDoesNotContain(repoRoot,
        "build.sh",
        "logicflow-debug-helper.yml",
        "OUROBOROS_LOGICFLOW_DEBUG_HELPER_ARTIFACT");
    assertFileContains(repoRoot,
        "scripts/build-local-develop.sh",
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper",
        "ouroboros-logicflow-debug-helper-${VERSION}.jar",
        "${DEVELOP_SUPPORT_PATH}/ouroboros-logicflow-debug-helper.jar");
    assertFileDoesNotContain(repoRoot,
        "scripts/build-local-develop.sh",
        "logicflow-debug-helper.yml",
        "OUROBOROS_LOGICFLOW_DEBUG_HELPER_ARTIFACT");
    assertFileDoesNotContain(repoRoot,
        "platform/ouroboros-develop/docker/root/app/metadata/application.yml",
        "OUROBOROS_LOGICFLOW_DEBUG_HELPER_ARTIFACT",
        "logicflow-debug-helper.yml");
  }

  @Test
  void mothershipStartScriptShouldPreserveCustomClassPaths(@TempDir Path tempDir) throws Exception {
    Path repoRoot = resolveRepoRoot();
    Path startScriptPath = repoRoot.resolve("platform/ouroboros-mothership/docker/root/app/base/service/start.sh");
    String startScript = Files.readString(startScriptPath, StandardCharsets.UTF_8);

    assertFalse(startScript.contains("echo \"${CUSTOM_CLASS_PATH}\" | tr ';' '\\n' | while read -r path; do"),
        "CUSTOM_CLASS_PATH must not be accumulated inside a pipe while loop");
    assertFalse(startScript.contains("echo \"${CUSTOM_BASE_CLASS_PATH}\" | tr ';' '\\n' | while read -r path; do"),
        "CUSTOM_BASE_CLASS_PATH must not be accumulated inside a pipe while loop");

    Path appRoot = Files.createDirectories(tempDir.resolve("app"));
    Path customPath = Files.createDirectories(appRoot.resolve("custom-classpath"));
    Path customBasePath = Files.createDirectories(appRoot.resolve("custom-base-classpath"));
    Files.createDirectories(appRoot.resolve("base/service/ouroboros-lib"));
    Files.createDirectories(appRoot.resolve("base/service/third-party-lib"));

    String probeScript = startScript
        .replace("APP_ROOT=/app", "APP_ROOT=" + shellSingleQuote(appRoot.toString()))
        .replace("exec java ${JAVA_PARAMS} ${JAR_PARAM} ${SPRING_BOOT_PARAMS}",
            "printf '%s\\n' \"${FINAL_CLASS_PATH}\"");
    Path probeScriptPath = tempDir.resolve("start-probe.sh");
    Files.writeString(probeScriptPath, probeScript, StandardCharsets.UTF_8);

    ProcessBuilder processBuilder = new ProcessBuilder("sh", probeScriptPath.toString());
    processBuilder.environment().put("CUSTOM_CLASS_PATH", customPath + ";" + tempDir.resolve("missing"));
    processBuilder.environment().put("CUSTOM_BASE_CLASS_PATH", customBasePath.toString());
    Process process = processBuilder.start();
    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertTrue(finished, "start.sh classpath probe should finish");

    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    assertEquals(0, process.exitValue(), () -> "start.sh classpath probe failed: " + stderr);
    assertTrue(stdout.startsWith("file:" + customPath),
        () -> "CUSTOM_CLASS_PATH should be first in FINAL_CLASS_PATH but was: " + stdout);
    assertTrue(stdout.contains(",file:" + customBasePath),
        () -> "CUSTOM_BASE_CLASS_PATH should remain in FINAL_CLASS_PATH but was: " + stdout);
  }

  @Test
  void debugHelperPomShouldUseOnlyAllowedOuroborosDependenciesAndSpringAutoConfiguration() throws IOException {
    Path repoRoot = resolveRepoRoot();
    Path pomPath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper/pom.xml");
    assertTrue(Files.exists(pomPath), () -> "Missing debug helper POM: " + pomPath);

    String pomSource = Files.readString(pomPath, StandardCharsets.UTF_8);
    List<String> requiredArtifactIds = List.of(
        "<artifactId>ouroboros-logicflow-core</artifactId>",
        "<artifactId>ouroboros-message-queue-core</artifactId>",
        "<artifactId>ouroboros-util</artifactId>",
        "<artifactId>spring-boot-autoconfigure</artifactId>"
    );
    for (String requiredArtifactId : requiredArtifactIds) {
      assertTrue(pomSource.contains(requiredArtifactId),
          () -> "Debug helper POM should depend on " + requiredArtifactId);
    }

    List<String> forbiddenArtifactIds = List.of(
        "<artifactId>ouroboros-logicflow-runtime</artifactId>",
        "<artifactId>ouroboros-logicflow-all</artifactId>",
        "<artifactId>ouroboros-logicflow-dev</artifactId>",
        "<artifactId>ouroboros-logicflow-web-adapter</artifactId>",
        "<artifactId>ouroboros-data-core</artifactId>",
        "<artifactId>ouroboros-dev-model-manage-webapi-scaffold</artifactId>",
        "<artifactId>ouroboros-web-security-core</artifactId>",
        "<artifactId>ouroboros-websocket-core</artifactId>",
        "<artifactId>common-app</artifactId>",
        "<artifactId>common-dev</artifactId>",
        "<artifactId>common-web</artifactId>"
    );
    for (String forbiddenArtifactId : forbiddenArtifactIds) {
      assertFalse(pomSource.contains(forbiddenArtifactId),
          () -> "Debug helper POM must not depend on " + forbiddenArtifactId);
    }

    Path autoConfigurationImports = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    Path springFactories = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper/src/main/resources/META-INF/spring.factories");
    String autoConfigurationClass = "com.ouroboros.logicflow.debug.LogicFlowDebugHelperAutoConfiguration";
    assertTrue(Files.readString(autoConfigurationImports, StandardCharsets.UTF_8).contains(autoConfigurationClass),
        "Debug helper must register Boot 3 auto configuration imports");
    assertTrue(Files.readString(springFactories, StandardCharsets.UTF_8).contains(autoConfigurationClass),
        "Debug helper must register spring.factories auto configuration");
  }

  @Test
  void internalAdapterNodeBuilderBundlesShouldPairBuildersWithNodeDefines() throws IOException {
    Path repoRoot = resolveRepoRoot();

    assertNodeBuilderBundle(repoRoot,
        "core/ouroboros-logicflow/ouroboros-logicflow-data-model",
        new LinkedHashMap<String, String>() {{
          put("DeleteDataNode.flow-node.json", "deleteData");
          put("InsertDataNode.flow-node.json", "insertData");
          put("UpdateDataNode.flow-node.json", "updateData");
          put("PartialUpdateDataNode.flow-node.json", "partialUpdateData");
          put("SearchDataNode.flow-node.json", "searchData");
          put("PagedSearchDataNode.flow-node.json", "pagedSearchData");
          put("BatchInsertDataNode.flow-node.json", "batchInsertData");
          put("NoramlizeWhereNode.flow-node.json", "normalizeWhere");
          put("CountDataNode.flow-node.json", "countData");
          put("GetOneDataNode.flow-node.json", "getOneData");
          put("DataModelNode.flow-node.json", "dataModel");
        }});

    assertNodeBuilderBundle(repoRoot,
        "core/ouroboros-logicflow/ouroboros-logicflow-extra-nodes",
        new LinkedHashMap<String, String>() {{
          put("OuroborosExceptionNode.flow-node.json", "ouroborosExceptions");
          put("ReadExcelNode.flow-node.json", "readExcel");
        }});
  }

  @Test
  void platformAdaptersShouldConsumePublicContractsWithoutOwningAuthoringOrExecutionTruth() throws IOException {
    Path repoRoot = resolveRepoRoot();

    Path devControllerPath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/controller/LogicflowNodeDefineController.java");
    assertTrue(Files.exists(devControllerPath), () -> "Missing dev NodeDefine controller: " + devControllerPath);
    String devControllerSource = Files.readString(devControllerPath, StandardCharsets.UTF_8);
    assertTrue(devControllerSource.contains("LogicFlowNodeDefineProvider"),
        () -> "Dev NodeDefine API should consume LogicFlowNodeDefineProvider, not own a second authoring truth");

    Path webAdapterPath = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-web-adapter/src/main/java/com/ouroboros/logicflow/adapter/HandlerFunctionAdapter.java");
    assertTrue(Files.exists(webAdapterPath), () -> "Missing web adapter handler: " + webAdapterPath);
    String webAdapterSource = Files.readString(webAdapterPath, StandardCharsets.UTF_8);
    assertTrue(webAdapterSource.contains("LogicFlowExecutionFacade"),
        () -> "Web adapter should enter execution through LogicFlowExecutionFacade");
    assertFalse(webAdapterSource.contains("FlowModelCenter"),
        () -> "Web adapter should not rebuild the runtime execution chain with FlowModelCenter");

    Path runtimeDescriptorDir = repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-runtime/src/main/resources/META-INF/services");
    List<String> runtimeDescriptors = List.of(
        "com.ouroboros.logicflow.model.FlowModelMetaProvider",
        "com.ouroboros.logicflow.model.FlowModelProvider",
        "com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine"
    );
    for (String runtimeDescriptor : runtimeDescriptors) {
      assertTrue(Files.exists(runtimeDescriptorDir.resolve(runtimeDescriptor)),
          () -> "Runtime adapter should expose provider SPI descriptor: " + runtimeDescriptor);
    }
    assertFalse(Files.exists(runtimeDescriptorDir.resolve("com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer")),
        "Runtime adapter must not expose debug analyzer SPI descriptor by default");
    assertTrue(Files.exists(repoRoot.resolve(
        "core/ouroboros-logicflow/ouroboros-logicflow-debug-helper/src/main/resources/META-INF/services/com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer")),
        "Debug helper module should own FlowRuntimeAnalyzer SPI descriptor");
  }

  @Test
  void editorShouldNotExposeFrontendBuiltInNodeDefineSource() throws IOException {
    Path repoRoot = resolveRepoRoot();
    assertFileDoesNotContain(repoRoot,
        "frontends/web/ouroboros-logicflow-editor/src/node-define/index.js",
        "getBuiltInNodeDefines");
    assertFileDoesNotContain(repoRoot,
        "frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/state/useLogicFlowEditorState.js",
        "disableBuiltIn", "overwriteBuiltIn", "mergeNodeDefineSources", "getBuiltInNodeDefines");
    assertFileDoesNotContain(repoRoot,
        "frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/state/editorStateHelpers.mjs",
        "mergeNodeDefineSources");

    Path frontendSwitchDefinePath = repoRoot.resolve(
        "frontends/web/ouroboros-logicflow-editor/src/node-define/switch.js");
    assertFalse(Files.exists(frontendSwitchDefinePath),
        () -> "Frontend switch built-in node define should not remain as a second authoring source: "
            + frontendSwitchDefinePath);
  }

  @Test
  void editorShouldNotContainLegacySemanticFallbacks() throws IOException {
    Path repoRoot = resolveRepoRoot();
    assertFileDoesNotContain(repoRoot,
        "frontends/web/ouroboros-logicflow-editor/src/node-define/editorCapabilityDefaults.mjs",
        "legacyTypeOverrides");
    assertFileDoesNotContain(repoRoot,
        "frontends/web/ouroboros-logicflow-editor/src/node-define/editorCapabilities.js",
        "legacyTypeOverrides");
    assertFileDoesNotContain(repoRoot,
        "frontends/web/ouroboros-logicflow-editor/src/node/outputPortNormalization.mjs",
        "legacySelectiveOutputConfigs", "SWITCH_EXCLUSIVE_GROUP_ID");
  }

  private Path resolveRepoRoot() {
    String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
    if (mavenRoot != null && !mavenRoot.trim().isEmpty()) {
      return Paths.get(mavenRoot).toAbsolutePath().normalize();
    }

    Path current = Paths.get("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.exists(current.resolve("pom.xml"))
          && Files.exists(current.resolve("testing/architecture-tests/pom.xml"))) {
        return current;
      }
      current = current.getParent();
    }
    return Paths.get("").toAbsolutePath().normalize();
  }

  private List<Path> findSourceFiles(Path repoRoot, String relativeSuffix) throws IOException {
    List<Path> searchRoots = List.of(
        repoRoot.resolve("core/ouroboros-logicflow"),
        repoRoot.resolve("ability/ouroboros-message-queue"),
        repoRoot.resolve("ability/ouroboros-configuration")
    );

    List<Path> results = new ArrayList<Path>();
    for (Path searchRoot : searchRoots) {
      if (!Files.exists(searchRoot)) {
        continue;
      }
      try (Stream<Path> pathStream = Files.walk(searchRoot)) {
        pathStream
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().contains("/src/main/resources/"))
            .filter(path -> path.toString().endsWith(relativeSuffix))
            .filter(path -> !path.toString().contains("/target/"))
            .forEach(results::add);
      }
    }
    return results.stream().sorted().collect(Collectors.toList());
  }

  private List<String> readProviderEntries(Path descriptorPath) throws IOException {
    List<String> providers = new ArrayList<String>();
    for (String line : Files.readAllLines(descriptorPath, StandardCharsets.UTF_8)) {
      String provider = line.trim();
      if (!provider.isEmpty() && !provider.startsWith("#")) {
        providers.add(provider);
      }
    }
    return providers;
  }

  private String extractJsonString(String source, String fieldName, Path sourcePath) {
    String marker = "\"" + fieldName + "\"";
    int fieldIndex = source.indexOf(marker);
    assertTrue(fieldIndex >= 0, () -> "Missing JSON field " + fieldName + " in " + sourcePath);

    int colonIndex = source.indexOf(':', fieldIndex + marker.length());
    assertTrue(colonIndex >= 0, () -> "Missing JSON field separator for " + fieldName + " in " + sourcePath);

    int openQuoteIndex = source.indexOf('"', colonIndex + 1);
    assertTrue(openQuoteIndex >= 0, () -> "Missing JSON string opening quote for " + fieldName + " in " + sourcePath);

    int closeQuoteIndex = source.indexOf('"', openQuoteIndex + 1);
    assertTrue(closeQuoteIndex > openQuoteIndex,
        () -> "Missing JSON string closing quote for " + fieldName + " in " + sourcePath);
    return source.substring(openQuoteIndex + 1, closeQuoteIndex);
  }

  private void assertOfficialBuilderSourceExists(Path repoRoot, String builderSourceFileName) {
    Path builderPath = repoRoot.resolve(
            "core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes")
        .resolve(builderSourceFileName);

    assertTrue(Files.exists(builderPath),
        () -> "Missing official builder source for authoring node define: " + builderPath);
  }

  private void assertNodeBuilderBundle(
      Path repoRoot,
      String moduleRoot,
      LinkedHashMap<String, String> expectedNodeDefineTypes
  ) throws IOException {
    Path modulePath = repoRoot.resolve(moduleRoot);
    Path descriptorPath = modulePath.resolve(
        "src/main/resources/META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder");
    Path nodeDefineDir = modulePath.resolve(
        "src/main/metadata");
    assertTrue(Files.exists(descriptorPath), () -> "Missing internal adapter NodeBuilder descriptor: " + descriptorPath);
    assertTrue(Files.isDirectory(nodeDefineDir), () -> "Missing internal adapter NodeDefine directory: " + nodeDefineDir);

    List<String> builderProviders = readProviderEntries(descriptorPath);
    assertTrue(builderProviders.size() == expectedNodeDefineTypes.size(),
        () -> "Builder provider count should match NodeDefine JSON count for " + moduleRoot
            + ": providers=" + builderProviders + ", expected json=" + expectedNodeDefineTypes.keySet());

    Set<String> actualNodeDefineFiles;
    try (Stream<Path> pathStream = Files.list(nodeDefineDir)) {
      actualNodeDefineFiles = pathStream
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(fileName -> fileName.endsWith(".json"))
          .collect(Collectors.toSet());
    }
    assertTrue(actualNodeDefineFiles.equals(expectedNodeDefineTypes.keySet()),
        () -> "Internal adapter NodeDefine JSON files should match expected bundle for " + moduleRoot
            + ": actual=" + actualNodeDefineFiles + ", expected=" + expectedNodeDefineTypes.keySet());

    for (String builderProvider : builderProviders) {
      assertInternalBuilderSourceExists(modulePath, builderProvider);
    }
    for (Map.Entry<String, String> expectedType : expectedNodeDefineTypes.entrySet()) {
      Path nodeDefinePath = nodeDefineDir.resolve(expectedType.getKey());
      String jsonSource = Files.readString(nodeDefinePath, StandardCharsets.UTF_8);
      String nodeType = extractJsonString(jsonSource, "type", nodeDefinePath);
      assertTrue(expectedType.getValue().equals(nodeType),
          () -> "Internal adapter NodeDefine " + nodeDefinePath + " should declare type "
              + expectedType.getValue() + " but was " + nodeType);
    }
  }

  private void assertSourceBundleMatchesClassification(
      Path repoRoot,
      BundleClassification bundle
  ) throws IOException {
    Path modulePath = repoRoot.resolve(bundle.moduleSource);
    Path descriptorPath = modulePath.resolve(bundle.nodeBuilderDescriptor);
    Path nodeDefineDir = modulePath.resolve(bundle.nodeDefineResourceDir).normalize();

    assertTrue(Files.exists(descriptorPath),
        () -> "Missing classified NodeBuilder descriptor for " + bundle.id + ": " + descriptorPath);
    assertTrue(Files.isDirectory(nodeDefineDir),
        () -> "Missing classified NodeDefine directory for " + bundle.id + ": " + nodeDefineDir);

    List<String> providers = readProviderEntries(descriptorPath);
    assertEquals(bundle.builderCount, providers.size(),
        () -> "Classified builder count should match descriptor for " + bundle.id);
    assertTrue(providers.containsAll(bundle.builderProviders()),
        () -> "Descriptor providers should include classified providers for " + bundle.id
            + ": classified=" + bundle.builderProviders() + ", actual=" + providers);

    Map<String, String> actualNodeDefineTypes = readNodeDefineTypes(nodeDefineDir);
    assertEquals(bundle.nodeDefineJsonCount, actualNodeDefineTypes.size(),
        () -> "Classified NodeDefine JSON count should match resources for " + bundle.id);
    assertEquals(bundle.nodeDefineTypes(), actualNodeDefineTypes,
        () -> "Classified NodeDefine files/types should match resources for " + bundle.id);

    if (!"test-only".equals(bundle.targetVisibility)) {
      for (NodeClassification node : bundle.nodes) {
        assertClassifiedBuilderSourceExists(modulePath, node);
      }
    }
  }

  private Map<String, String> readNodeDefineTypes(Path nodeDefineDir) throws IOException {
    Map<String, String> typesByFile = new LinkedHashMap<String, String>();
    try (Stream<Path> pathStream = Files.list(nodeDefineDir)) {
      List<Path> nodeDefinePaths = pathStream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .sorted()
          .collect(Collectors.toList());
      for (Path nodeDefinePath : nodeDefinePaths) {
        String jsonSource = Files.readString(nodeDefinePath, StandardCharsets.UTF_8);
        typesByFile.put(nodeDefinePath.getFileName().toString(),
            extractJsonString(jsonSource, "type", nodeDefinePath));
      }
    }
    return typesByFile;
  }

  private void assertClassifiedBuilderSourceExists(Path modulePath, NodeClassification node) {
    Path sourceRoot = modulePath.resolve("src/main/java/com/ouroboros/logicflow/nodes");
    Path builderPath = sourceRoot.resolve(node.builderSource);

    assertTrue(Files.exists(builderPath),
        () -> "Missing classified builder source for " + node.type + ": " + builderPath);
  }

  private Map<String, BundleClassification> readBundleClassificationManifest(Path repoRoot)
      throws IOException {
    Path manifestPath = repoRoot.resolve(
        "scripts/open-source/manifests/logicflow/logicflow-extension-bundle-classification.yaml");
    assertTrue(Files.exists(manifestPath), () -> "Missing LogicFlow bundle classification manifest: " + manifestPath);

    Map<String, BundleClassification> bundles = new LinkedHashMap<String, BundleClassification>();
    BundleClassification currentBundle = null;
    NodeClassification currentNode = null;
    boolean inBundles = false;
    boolean inNodes = false;

    for (String rawLine : Files.readAllLines(manifestPath, StandardCharsets.UTF_8)) {
      String line = rawLine.stripTrailing();
      if ("bundles:".equals(line)) {
        inBundles = true;
        continue;
      }
      if (!inBundles) {
        continue;
      }
      if ("evidence:".equals(line)) {
        break;
      }
      if (line.startsWith("  - id: ")) {
        currentBundle = new BundleClassification(valueAfter(line, "  - id: "));
        bundles.put(currentBundle.id, currentBundle);
        currentNode = null;
        inNodes = false;
        continue;
      }
      if (currentBundle == null) {
        continue;
      }
      if ("    nodes:".equals(line)) {
        inNodes = true;
        currentNode = null;
        continue;
      }
      if (inNodes && line.startsWith("      - type: ")) {
        currentNode = new NodeClassification(valueAfter(line, "      - type: "));
        currentBundle.nodes.add(currentNode);
        continue;
      }
      if (inNodes && currentNode != null && line.startsWith("        ")) {
        applyNodeField(currentNode, line);
        continue;
      }
      if (!inNodes && line.startsWith("    ")) {
        applyBundleField(currentBundle, line);
      }
    }
    return bundles;
  }

  private void applyBundleField(BundleClassification bundle, String line) {
    if (line.startsWith("    module: ")) {
      bundle.module = valueAfter(line, "    module: ");
    } else if (line.startsWith("    module_source: ")) {
      bundle.moduleSource = valueAfter(line, "    module_source: ");
    } else if (line.startsWith("    target_visibility: ")) {
      bundle.targetVisibility = valueAfter(line, "    target_visibility: ");
    } else if (line.startsWith("    export_policy: ")) {
      bundle.exportPolicy = valueAfter(line, "    export_policy: ");
    } else if (line.startsWith("    dependency_class: ")) {
      bundle.dependencyClass = valueAfter(line, "    dependency_class: ");
    } else if (line.startsWith("    node_builder_descriptor: ")) {
      bundle.nodeBuilderDescriptor = valueAfter(line, "    node_builder_descriptor: ");
    } else if (line.startsWith("    node_define_resource_dir: ")) {
      bundle.nodeDefineResourceDir = valueAfter(line, "    node_define_resource_dir: ");
    } else if (line.startsWith("    builder_count: ")) {
      bundle.builderCount = Integer.parseInt(valueAfter(line, "    builder_count: "));
    } else if (line.startsWith("    node_define_json_count: ")) {
      bundle.nodeDefineJsonCount = Integer.parseInt(valueAfter(line, "    node_define_json_count: "));
    }
  }

  private void applyNodeField(NodeClassification node, String line) {
    if (line.startsWith("        node_define: ")) {
      node.nodeDefine = valueAfter(line, "        node_define: ");
    } else if (line.startsWith("        builder_provider: ")) {
      node.builderProvider = valueAfter(line, "        builder_provider: ");
    } else if (line.startsWith("        builder_source: ")) {
      node.builderSource = valueAfter(line, "        builder_source: ");
    } else if (line.startsWith("        required_capability: ")) {
      node.requiredCapability = Boolean.parseBoolean(valueAfter(line, "        required_capability: "));
    }
  }

  private String valueAfter(String line, String prefix) {
    String value = line.substring(prefix.length()).trim();
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static class BundleClassification {

    private final String id;
    private String module;
    private String moduleSource;
    private String targetVisibility;
    private String exportPolicy;
    private String dependencyClass;
    private String nodeBuilderDescriptor;
    private String nodeDefineResourceDir;
    private int builderCount;
    private int nodeDefineJsonCount;
    private final List<NodeClassification> nodes = new ArrayList<NodeClassification>();

    BundleClassification(String id) {
      this.id = id;
    }

    boolean hasNodeType(String nodeType) {
      return nodes.stream().anyMatch(node -> node.type.equals(nodeType));
    }

    boolean hasRequiredCapability(String nodeType) {
      return nodes.stream().anyMatch(node -> node.type.equals(nodeType) && node.requiredCapability);
    }

    Set<String> nodeTypes() {
      return nodes.stream()
          .map(node -> node.type)
          .collect(Collectors.toSet());
    }

    Set<String> builderProviders() {
      return nodes.stream()
          .map(node -> node.builderProvider)
          .collect(Collectors.toSet());
    }

    Map<String, String> nodeDefineTypes() {
      Map<String, String> nodeDefineTypes = new LinkedHashMap<String, String>();
      nodes.stream()
          .sorted((left, right) -> left.nodeDefine.compareTo(right.nodeDefine))
          .forEach(node -> nodeDefineTypes.put(node.nodeDefine, node.type));
      return nodeDefineTypes;
    }
  }

  private static class NodeClassification {

    private final String type;
    private String nodeDefine;
    private String builderProvider;
    private String builderSource;
    private boolean requiredCapability;

    NodeClassification(String type) {
      this.type = type;
    }
  }

  private void assertInternalBuilderSourceExists(Path modulePath, String providerName) {
    String javaFileName = providerName.substring(providerName.lastIndexOf('.') + 1)
        .replace("$Builder", ".java");
    Path builderPath = modulePath.resolve("src/main/java/com/ouroboros/logicflow/nodes").resolve(javaFileName);

    assertTrue(Files.exists(builderPath),
        () -> "Missing internal adapter builder source for descriptor provider " + providerName + ": " + builderPath);
  }

  private void assertJavaSourcesDoNotContain(
      Path sourceRoot,
      List<String> forbiddenTexts,
      String messagePrefix
  ) throws IOException {
    try (Stream<Path> pathStream = Files.walk(sourceRoot)) {
      List<Path> javaSources = pathStream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .collect(Collectors.toList());
      for (Path javaSource : javaSources) {
        String source = Files.readString(javaSource, StandardCharsets.UTF_8);
        for (String forbiddenText : forbiddenTexts) {
          assertFalse(source.contains(forbiddenText),
              () -> messagePrefix + " " + forbiddenText + " in " + javaSource);
        }
      }
    }
  }

  private void assertFileDoesNotContain(Path repoRoot, String relativePath, String... forbiddenTexts) throws IOException {
    Path path = repoRoot.resolve(relativePath);
    assertTrue(Files.exists(path), () -> "Missing source file: " + path);
    String source = Files.readString(path, StandardCharsets.UTF_8);
    for (String forbiddenText : forbiddenTexts) {
      assertFalse(source.contains(forbiddenText),
          () -> path + " should not contain legacy LogicFlow path marker: " + forbiddenText);
    }
  }

  private void assertFileContains(Path repoRoot, String relativePath, String... requiredTexts) throws IOException {
    Path path = repoRoot.resolve(relativePath);
    assertTrue(Files.exists(path), () -> "Missing source file: " + path);
    String source = Files.readString(path, StandardCharsets.UTF_8);
    for (String requiredText : requiredTexts) {
      assertTrue(source.contains(requiredText),
          () -> path + " should contain LogicFlow debug helper marker: " + requiredText);
    }
  }

  private String shellSingleQuote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }
}
