package com.ouroboros.businessmodel.util;

import java.util.Map;

import com.ouroboros.template.JarTemplateEngine;

public final class EditorSchemaTemplates {
  final static String TEMPLATE_PATH = "META-INF/editor-schema-templates/";
  final static JarTemplateEngine TEMPLATE_ENGINE = new JarTemplateEngine(TEMPLATE_PATH);

  public static String generate(String templateName, Map<String, ?> config) {
    return TEMPLATE_ENGINE.generate(templateName, config);
  }

  public static <T> T generate(String templateName, Map<String, ?> config, Class<T> clazz) {
    return TEMPLATE_ENGINE.generate(templateName, config, clazz);
  }
}
