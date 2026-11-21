package com.ouroboros.ability.all.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

class BomSpringAiSelectionContractTest {

  private static final String OUROBOROS_VERSION = "${ouroboros.version}";

  @Test
  void sourceBomImportsSpringAiLineAndManagesStarterFamily() throws Exception {
    var bom = parseXml(projectRoot().resolve("bom/pom.xml"));
    var managedDependencies = managedDependencies(bom);

    assertThat(propertyValue(bom, "spring-ai.version")).isEqualTo("2.0.0");
    assertThat(managedDependencies)
        .anySatisfy(dependency -> assertThat(dependency)
            .usingRecursiveComparison()
            .isEqualTo(new DependencyCoordinate("org.springframework.ai", "spring-ai-bom", "${spring-ai.version}", "pom", "import")));
    assertThat(managedDependencies)
        .filteredOn(dependency -> dependency.groupId.equals("org.springframework.ai"))
        .extracting(dependency -> dependency.version)
        .doesNotContain("1.1.8");

    assertThat(managedDependencies)
        .filteredOn(dependency -> dependency.groupId.equals("com.ouroboros"))
        .filteredOn(dependency -> dependency.artifactId.startsWith("ouroboros-ai-starter"))
        .extracting(dependency -> dependency.artifactId + ":" + dependency.version)
        .contains(
            "ouroboros-ai-starter:" + OUROBOROS_VERSION,
            "ouroboros-ai-starter-legacy:" + OUROBOROS_VERSION,
            "ouroboros-ai-starter-runtime:" + OUROBOROS_VERSION,
            "ouroboros-ai-starter-openai:" + OUROBOROS_VERSION,
            "ouroboros-ai-starter-anthropic:" + OUROBOROS_VERSION,
            "ouroboros-ai-starter-spring-ai:" + OUROBOROS_VERSION
        );
    assertThat(managedDependencies)
        .anySatisfy(dependency -> assertCoordinate(dependency, "com.openai", "openai-java", "${openai.java.version}"))
        .anySatisfy(dependency -> assertCoordinate(dependency, "com.anthropic", "anthropic-java", "${anthropic.java.version}"));
  }

  @Test
  void effectiveBomResolvesSpringAiManagedDependenciesWithoutBoot4Line() throws Exception {
    var effectivePom = generateEffectivePom();

    var effectiveBom = parseXml(effectivePom);
    var managedDependencies = managedDependencies(effectiveBom);
    var springAiDependencies = managedDependencies.stream()
        .filter(dependency -> dependency.groupId.equals("org.springframework.ai"))
        .collect(Collectors.toList());

    assertThat(propertyValue(effectiveBom, "spring-ai.version")).isEqualTo("2.0.0");
    assertThat(springAiDependencies).isNotEmpty();
    assertThat(springAiDependencies)
        .extracting(dependency -> dependency.version)
        .doesNotContain("1.1.8");
  }

  private static void assertCoordinate(DependencyCoordinate dependency, String groupId, String artifactId, String version) {
    assertThat(dependency.groupId).isEqualTo(groupId);
    assertThat(dependency.artifactId).isEqualTo(artifactId);
    assertThat(dependency.version).isEqualTo(version);
  }

  private static Path projectRoot() {
    return moduleRoot().getParent().getParent().getParent();
  }

  private static Path moduleRoot() {
    return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
  }

  private static Path generateEffectivePom() throws Exception {
    var effectivePom = projectRoot().resolve("bom/target/effective-pom.xml");
    Files.deleteIfExists(effectivePom);
    // 跟随仓库内的本地仓约定（build.sh / preflight 通过 MAVEN_REPO_LOCAL 指定），
    // 否则受限环境下嵌套 mvn 会去写 ~/.m2 并失败。
    var command = new ArrayList<>(List.of(
        "mvn",
        "-q",
        "-f",
        projectRoot().resolve("bom/pom.xml").toString(),
        "help:effective-pom",
        "-Doutput=target/effective-pom.xml"));
    var mavenRepoLocal = System.getenv("MAVEN_REPO_LOCAL");
    if (mavenRepoLocal != null && !mavenRepoLocal.isBlank()) {
      command.add("-Dmaven.repo.local=" + mavenRepoLocal);
    }
    var process = new ProcessBuilder(command)
        .directory(projectRoot().toFile())
        .redirectErrorStream(true)
        .start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor()).as(output).isZero();
    assertThat(Files.exists(effectivePom)).as(output).isTrue();
    return effectivePom;
  }

  private static Document parseXml(Path path) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setExpandEntityReferences(false);
    factory.setNamespaceAware(false);
    return factory.newDocumentBuilder().parse(path.toFile());
  }

  private static String propertyValue(Document document, String propertyName) {
    return childElements(firstChild(document.getDocumentElement(), "properties"), propertyName).stream()
        .map(Element::getTextContent)
        .map(String::trim)
        .findFirst()
        .orElse(null);
  }

  private static List<DependencyCoordinate> managedDependencies(Document document) {
    var dependencyManagement = firstChild(document.getDocumentElement(), "dependencyManagement");
    var dependencies = firstChild(dependencyManagement, "dependencies");
    return childElements(dependencies, "dependency").stream()
        .map(dependency -> new DependencyCoordinate(
            childText(dependency, "groupId"),
            childText(dependency, "artifactId"),
            childText(dependency, "version"),
            childText(dependency, "type"),
            childText(dependency, "scope")
        ))
        .collect(Collectors.toList());
  }

  private static Element firstChild(Element parent, String name) {
    if (parent == null) {
      return null;
    }
    return childElements(parent, name).stream().findFirst().orElse(null);
  }

  private static List<Element> childElements(Element parent, String name) {
    var elements = new ArrayList<Element>();
    if (parent == null) {
      return elements;
    }
    var children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      var child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && Objects.equals(child.getNodeName(), name)) {
        elements.add((Element) child);
      }
    }
    return elements;
  }

  private static String childText(Element parent, String name) {
    return childElements(parent, name).stream()
        .map(Element::getTextContent)
        .map(String::trim)
        .findFirst()
        .orElse("");
  }

  private static final class DependencyCoordinate {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final String scope;

    private DependencyCoordinate(String groupId, String artifactId, String version, String type, String scope) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.type = type;
      this.scope = scope;
    }
  }
}
