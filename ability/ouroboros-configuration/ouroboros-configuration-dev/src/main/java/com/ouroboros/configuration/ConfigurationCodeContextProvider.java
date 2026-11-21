package com.ouroboros.configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.exception.BusinessException;

/** Configuration values are templates. Names come from the public catalog, never Environment enumeration. */
public final class ConfigurationCodeContextProvider implements CodeContextProvider {
  public Set<String> resourceTypes() { return Set.of("configuration"); }
  public String environmentLanguage(CodeContextRequest request) { return "spel"; }
  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    if (!"template".equals(request.language()) || !Set.of("/defaultValue", "/value").contains(request.location().pointer())) {
      throw new BusinessException("此字段不是配置值模板");
    }
    var keys = ConfigurationKeyCatalog.describe(metadata.application()).stream()
        .filter(item -> !item.get("key").equals(request.draft().path("key").asText())).toList();
    boolean protectedEntry = metadata.application().getModule("configuration").stream()
        .filter(entry -> entry != null && request.draft().path("key").asText().equals(entry.getName()))
        .anyMatch(entry -> entry.getDetails() != null && Boolean.TRUE.equals(entry.getDetails().get("managementAccessPolicyRequired")));
    if (protectedEntry || request.draft().path("managementAccessPolicyRequired").asBoolean(false)) {
      return new CodeContextDescription("template", List.of(), TypeShape.scalar("string"), List.of(), false,
          Map.of("templateMode", "disabled"));
    }
    var bindings = keys.stream().map(item -> CodeBindings.binding(String.valueOf(item.get("key")),
        TypeShape.scalar("string"), "configuration-reference")).toList();
    return new CodeContextDescription("template", bindings, TypeShape.scalar("string"),
        List.of(Map.of("code", "configuration-order-dependent", "message", "引用值取决于配置求值顺序；提示不执行求值")), true,
        Map.of("templateMode", "expression-only", "configurationKeys", keys, "configurationMarker", "$"));
  }
}
