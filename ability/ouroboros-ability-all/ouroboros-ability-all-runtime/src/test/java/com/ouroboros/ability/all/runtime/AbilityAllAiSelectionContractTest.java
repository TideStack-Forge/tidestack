package com.ouroboros.ability.all.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatMessage;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.TextContentPart;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.runtime.AiRuntimeAutoConfiguration;
import com.ouroboros.ai.runtime.provider.AiModelDescriptorProvider;
import com.ouroboros.ai.runtime.service.AiInvocationService;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;
import com.ouroboros.ai.runtime.spi.AiEmbeddingAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbilityAllAiSelectionContractTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AiRuntimeAutoConfiguration.class))
      .withUserConfiguration(TestApplication.class);

  @Test
  void abilityAllProductionPomSelectsOnlyProviderNeutralAiStarter() throws Exception {
    var pom = parseXml(moduleRoot().resolve("pom.xml"));

    assertThat(productionDependencies(pom).stream()
        .filter(dependency -> dependency.groupId.equals("com.ouroboros"))
        .filter(dependency -> dependency.artifactId.startsWith("ouroboros-ai"))
        .map(dependency -> dependency.groupId + ":" + dependency.artifactId)
        .collect(Collectors.toList()))
        .containsExactly("com.ouroboros:ouroboros-ai-starter-runtime");
  }

  @Test
  void abilityAllContextKeepsAiRuntimeProviderNeutral() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(AiInvocationService.class);
      assertThat(context.getBeansOfType(AiChatAdapter.class)).isEmpty();
      assertThat(context.getBeansOfType(AiEmbeddingAdapter.class)).isEmpty();

      var aiInvocationService = context.getBean(AiInvocationService.class);
      assertThatThrownBy(() -> aiInvocationService.chat(chatRequest()))
          .isInstanceOf(AiInvocationException.class)
          .hasMessage("No chat adapter registered for provider family: OPENAI");
    });
  }

  private static ChatRequest chatRequest() {
    return ChatRequest.builder()
        .modelId("openai-contract-model")
        .message(ChatMessage.builder()
            .role(ChatMessageRole.USER)
            .addContentPart(TextContentPart.of("hello"))
            .build())
        .build();
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

  private static List<DependencyCoordinate> productionDependencies(Document document) {
    var dependencies = firstChild(document.getDocumentElement(), "dependencies");
    return childElements(dependencies, "dependency").stream()
        .map(dependency -> new DependencyCoordinate(
            childText(dependency, "groupId"),
            childText(dependency, "artifactId"),
            childText(dependency, "scope")
        ))
        .filter(dependency -> dependency.scope.isEmpty() || dependency.scope.equals("compile") || dependency.scope.equals("runtime"))
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
    private final String scope;

    private DependencyCoordinate(String groupId, String artifactId, String scope) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.scope = scope;
    }
  }

  @SpringBootConfiguration
  static class TestApplication {

    @Bean
    AiModelDescriptorProvider aiModelDescriptorProvider() {
      return () -> Collections.singletonList(AiModelDescriptor.builder()
          .modelId("openai-contract-model")
          .providerFamily(AiProviderFamily.OPENAI)
          .build());
    }
  }
}
