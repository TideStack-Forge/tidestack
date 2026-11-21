package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateSummary;
import com.ouroboros.dev.projectgeneration.ui.ProjectGenerationAmisSchemaFactory;

class ProjectGenerationAmisSchemaFactoryTest {

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private final ProjectGenerationAmisSchemaFactory factory = new ProjectGenerationAmisSchemaFactory(null);

  @Test
  void shouldBuildNativeAmisFormWithPlainParameterItems() throws Exception {
    JsonNode form = factory.buildForm("demo", new ProjectTemplateCatalogView(
        "cat_42",
        "2.0.0",
        ProjectTemplateScope.MICRO_FRONTEND,
        false,
        List.of(template())
    ));

    assertEquals("form", form.path("type").asText());
    assertEquals("demo", form.path("data").path("appName").asText());
    assertEquals("", form.path("data").path("packageName").asText());
    assertTrue(form.path("wrapWithPanel").asBoolean());
    assertEquals("list-select", form.path("body").get(0).path("type").asText());
    assertEquals("tpl", form.path("body").get(0).path("itemSchema").path("type").asText());
    assertEquals("button-group-select", form.path("body").get(1).path("type").asText());
    assertEquals("React", form.path("body").get(1).path("options").get(0).path("label").asText());
    assertEquals("framework", form.path("body").get(1).path("name").asText());
    assertEquals("input-text", form.path("body").get(3).path("type").asText());
    assertEquals("packageName", form.path("body").get(3).path("name").asText());
    assertTrue(form.path("body").get(3).path("visibleOn").asText().contains("template.templateId"));
    assertFalse(form.path("body").get(3).has("api"));
    assertEquals("/api/dev/${appName}/project-generations", form.path("api").path("url").asText());
    assertEquals("blob", form.path("api").path("responseType").asText());
    assertEquals("submit", form.path("actions").get(1).path("type").asText());
  }

  @Test
  void shouldConvertParameterContractToPlainAmisFormItems() throws Exception {
    List<JsonNode> items = factory.buildParameterItems(template());

    assertEquals(4, items.size());
    assertEquals("button-group-select", items.get(0).path("type").asText());
    assertEquals("framework", items.get(0).path("name").asText());
    assertEquals("React", items.get(0).path("options").get(0).path("label").asText());
    assertEquals("button-group-select", items.get(1).path("type").asText());
    assertEquals("${framework === 'react'}", items.get(1).path("options").get(0).path("visibleOn").asText());
    assertEquals("input-text", items.get(2).path("type").asText());
    assertEquals("packageName", items.get(2).path("name").asText());
    assertTrue(items.get(2).path("required").asBoolean());
    assertEquals("@your-scope/customer-portal", items.get(2).path("placeholder").asText());
    assertEquals("switch", items.get(3).path("type").asText());
    assertTrue(items.get(3).path("value").asBoolean());
  }



  private static ProjectTemplateSummary template() throws Exception {
    return new ProjectTemplateSummary(
        "frontend.micro-app",
        "1.0.0",
        "frontend.micro-app-1.0.0-platform-2.0.0",
        "前端应用",
        "生成可挂载到平台的前端应用。",
        "stable",
        read("""
            {
              "type": "object",
              "x-ouroboros-project-root": "packageName",
              "additionalProperties": false,
              "required": ["framework", "buildTool", "packageName"],
              "properties": {
                "framework": {
                  "type": "string",
                  "title": "技术栈",
                  "default": "react",
                  "maxLength": 32,
                  "enum": ["react", "vue"]
                },
                "buildTool": {
                  "type": "string",
                  "title": "构建方式",
                  "default": "webpack",
                  "maxLength": 32,
                  "enum": ["webpack", "vite"]
                },
                "packageName": {
                  "type": "string",
                  "title": "npm 包名",
                  "maxLength": 214,
                  "x-ouroboros-format": "npm-package-name"
                },
                "includeExampleRoute": {
                  "type": "boolean",
                  "title": "包含示例路由",
                  "default": true
                }
              }
            }
            """),
        read("""
            {
              "framework": {
                "order": 1,
                "control": "button-group-select",
                "optionLabels": {"react": "React", "vue": "Vue"}
              },
              "buildTool": {
                "order": 2,
                "control": "button-group-select",
                "optionLabels": {"webpack": "Webpack", "vite": "Vite"},
                "optionVisibleWhen": {
                  "webpack": "${framework === 'react'}",
                  "vite": "${framework === 'vue'}"
                }
              },
              "packageName": {"order": 3, "control": "text", "placeholder": "@your-scope/customer-portal"},
              "includeExampleRoute": {"order": 5, "control": "toggle"}
            }
            """)
    );
  }

  private static JsonNode read(String json) throws Exception {
    return JSON.readTree(json);
  }
}
