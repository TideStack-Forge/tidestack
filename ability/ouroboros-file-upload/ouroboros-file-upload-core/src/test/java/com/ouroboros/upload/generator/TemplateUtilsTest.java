package com.ouroboros.upload.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TemplateUtilsTest {

  @Test
  void processRandomPartShouldReplaceEveryRandomPlaceholder() {
    String template = "prefix-{random:4}-middle-{random:6}";

    String result = TemplateUtils.processRandomPart(template);

    assertFalse(result.contains("{random:"));
  }
}
