package com.ouroboros.ability.all.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiBomCompatibilityTest {

  @Test
  void isolatedFixturePinsBoot35AndSpringAi200() throws Exception {
    var fixturePom = moduleRoot().resolve("src/test/resources/spring-ai-bom-compatibility/pom.xml");

    assertThat(Files.exists(fixturePom)).isTrue();

    var pom = parseXml(fixturePom);
    assertThat(propertyValue(pom, "spring-boot.version")).isEqualTo("3.5.11");
    assertThat(propertyValue(pom, "spring-ai.version")).isEqualTo("2.0.0");
    assertThat(dependencies(pom).stream()
        .map(dependency -> dependency.groupId + ":" + dependency.artifactId)
        .collect(Collectors.toList()))
        .contains(
            "org.springframework.ai:spring-ai-autoconfigure-model-openai",
            "org.springframework.ai:spring-ai-openai");
  }

  private static Path moduleRoot() {
    return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
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

  private static java.util.List<DependencyCoordinate> dependencies(Document document) {
    var dependencies = firstChild(document.getDocumentElement(), "dependencies");
    return childElements(dependencies, "dependency").stream()
        .map(dependency -> new DependencyCoordinate(childText(dependency, "groupId"), childText(dependency, "artifactId")))
        .collect(Collectors.toList());
  }

  private static Element firstChild(Element parent, String name) {
    if (parent == null) {
      return null;
    }
    return childElements(parent, name).stream().findFirst().orElse(null);
  }

  private static java.util.List<Element> childElements(Element parent, String name) {
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

    private DependencyCoordinate(String groupId, String artifactId) {
      this.groupId = groupId;
      this.artifactId = artifactId;
    }
  }
}
