package com.ouroboros.ai.starter.springai;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

class StarterFamilyDependencyContractTest {

  @Test
  void starterPomsDeclareOnlyTheApprovedProductionDependencies() throws Exception {
    var aiRoot = Path.of(System.getProperty("user.dir")).getParent();

    assertThat(productionDependencies(aiRoot.resolve("ouroboros-ai-starter-runtime/pom.xml")))
        .containsExactly("com.ouroboros:ouroboros-ai-runtime");
    assertThat(productionDependencies(aiRoot.resolve("ouroboros-ai-starter-openai/pom.xml")))
        .containsExactly(
            "com.ouroboros:ouroboros-ai-starter-runtime",
            "com.ouroboros:ouroboros-ai-openai-runtime");
    assertThat(productionDependencies(aiRoot.resolve("ouroboros-ai-starter-anthropic/pom.xml")))
        .containsExactly(
            "com.ouroboros:ouroboros-ai-starter-runtime",
            "com.ouroboros:ouroboros-ai-anthropic-runtime");
    assertThat(productionDependencies(aiRoot.resolve("ouroboros-ai-starter-spring-ai/pom.xml")))
        .containsExactly(
            "com.ouroboros:ouroboros-ai-starter-openai",
            "com.ouroboros:ouroboros-ai-starter-anthropic");
    assertThat(productionDependencies(aiRoot.resolve("ouroboros-ai-starter/pom.xml")))
        .containsExactly("com.ouroboros:ouroboros-ai-starter-spring-ai");
  }

  @Test
  void springAiStarterClasspathDoesNotCarryRagMcpOrLangChain4j() {
    var classLoader = getClass().getClassLoader();

    assertThat(ClassUtils.isPresent("com.ouroboros.ai.rag.AiRagRuntimeAutoConfiguration", classLoader)).isFalse();
    assertThat(ClassUtils.isPresent("com.ouroboros.ai.mcp.AiMcpRuntimeAutoConfiguration", classLoader)).isFalse();
    assertThat(ClassUtils.isPresent("dev.langchain4j.model.chat.ChatLanguageModel", classLoader)).isFalse();
  }

  private static Set<String> productionDependencies(Path pom) throws Exception {
    var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = documentBuilderFactory.newDocumentBuilder().parse(pom.toFile());
    var dependencies = document.getElementsByTagName("dependency");
    var result = new LinkedHashSet<String>();
    for (var i = 0; i < dependencies.getLength(); i++) {
      var dependency = (Element) dependencies.item(i);
      if ("test".equals(text(dependency, "scope"))) {
        continue;
      }
      result.add(text(dependency, "groupId") + ":" + text(dependency, "artifactId"));
    }
    return result;
  }

  private static String text(Element parent, String tagName) {
    var nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() == 0) {
      return "";
    }
    return nodes.item(0).getTextContent().trim();
  }
}
