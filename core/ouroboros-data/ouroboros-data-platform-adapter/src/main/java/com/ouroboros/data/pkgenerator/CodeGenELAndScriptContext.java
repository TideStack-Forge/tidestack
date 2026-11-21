package com.ouroboros.data.pkgenerator;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.expression.ExpressionContextContributor;
import com.ouroboros.script.ScriptContextContributor;

@Component
public class CodeGenELAndScriptContext {
  public CodeGenELAndScriptContext(@Autowired FixLengthCodingGeneratorFactory codingGeneratorFactory) {
    CodeGen.codingGeneratorFactory = codingGeneratorFactory;
  }

  public static class CodeGen implements ExpressionContextContributor, ScriptContextContributor {
    private static FixLengthCodingGeneratorFactory codingGeneratorFactory;

    @Override
    public Map<String, Object> getBindings() {
      return Collections.singletonMap("codeGen", new CodeGenerator() {
        @Override
        public String get(String codingTemplate) {
          return Optional.ofNullable(codingGeneratorFactory)
              .flatMap(f -> f.get(codingTemplate))
              .map(g -> g.next(Collections.emptyMap()))
              .orElse(null);
        }

        @Override
        public String get(String codingTemplate, Map<String, Object> record) {
          return Optional.ofNullable(codingGeneratorFactory)
              .flatMap(f -> f.get(codingTemplate))
              .map(g -> g.next(record))
              .orElse(null);
        }

        @Override
        public String peek(String codingTemplate) {
          return peek(codingTemplate, Collections.emptyMap());
        }

        @Override
        public String peek(String codingTemplate, Map<String, Object> record) {
          return Optional.ofNullable(codingGeneratorFactory)
              .flatMap(f -> f.get(codingTemplate))
              .map(g -> g.peek(record))
              .orElse(null);
        }
      });
    }
  }
}
