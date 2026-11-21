package com.ouroboros.fieldtype.dev;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;

/** Describes only the backend parameter expansion phase of a field-type definition. */
public final class FieldTypeCodeContextProvider implements CodeContextProvider {
  public Set<String> resourceTypes() { return Set.of("field-type"); }
  public String environmentLanguage(CodeContextRequest request) { return "spel"; }
  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var pointer = request.location().pointer();
    if (!Set.of("spel", "template").contains(request.language()) || !pointer.matches("/schema/(dataType|primaryKeyGenerator|ui/(input|display))(/.*)?")) {
      throw new BusinessException("此字段不是字段类型的后端模板");
    }
    var bindings = new java.util.LinkedHashMap<String, ScriptIntelligenceGlobal>();
    for (var param : request.draft().path("schema").path("paramsUiSchema")) {
      if (param.hasNonNull("name")) bindings.put(param.path("name").asText(), CodeBindings.binding(param.path("name").asText(),
          param.has("value") ? CodeBindings.literalShape(param.path("value")) : TypeShape.unknown("undeclared-parameter-type"), "field-type-parameter"));
    }
    return new CodeContextDescription(request.language(), List.copyOf(bindings.values()), TypeShape.unknown("template-result"), List.of(), true,
        Map.of("templateMode", "expression-only", "embeddedExpressionMode", "template",
            "placeholders", pointer.startsWith("/schema/primaryKeyGenerator") ? List.of("yyyy", "yy", "MM", "dd", "HH", "mm", "ss") : List.of()));
  }
}
