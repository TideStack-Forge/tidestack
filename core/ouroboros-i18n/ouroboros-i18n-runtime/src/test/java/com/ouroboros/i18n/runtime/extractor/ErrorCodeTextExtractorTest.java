package com.ouroboros.i18n.runtime.extractor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTextExtractorTest {

  @Test
  void extractor_exportsRegistryDefinitionsIntoErrorsNamespace() {
    var extractor = new ErrorCodeTextExtractor(registry(definition(40301, "登录已过期")));
    var entries = extractor.extract().get();

    assertTrue(entries.stream().anyMatch(it -> "errors".equals(it.getNamespace()) && "40301".equals(it.getKey())));
  }

  @Test
  void extractor_usesDefaultMessageTemplateAsSourceText() {
    var extractor = new ErrorCodeTextExtractor(registry(definition(50000, "系统内部错误")));
    var entries = extractor.extract().get();

    assertTrue(entries.stream().anyMatch(it -> "系统内部错误".equals(it.getSourceText())));
  }

  private static ErrorCodeDefinition definition(int code, String message) {
    return new ErrorCodeDefinition()
        .setCode(code)
        .setTitle("CODE-" + code)
        .setDefaultMessageTemplate(message)
        .setEnabled(true);
  }

  private static ErrorCodeRegistry registry(ErrorCodeDefinition... definitions) {
    return new ErrorCodeRegistry() {
      @Override
      public Optional<ErrorCodeDefinition> getDefinition(Integer code) {
        return Arrays.stream(definitions).filter(it -> code.equals(it.getCode())).findFirst();
      }

      @Override
      public List<ErrorCodeDefinition> getAllDefinitions() {
        return Collections.unmodifiableList(Arrays.asList(definitions));
      }
    };
  }
}
