package com.ouroboros.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;

/** Config interpolation applies to all channels; Email alone owns its payload text replacement. */
public final class NotificationCodeContextProvider implements CodeContextProvider {
  public Set<String> resourceTypes() { return Set.of("notification"); }
  public String environmentLanguage(CodeContextRequest request) { return "spel"; }
  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var pointer = request.location().pointer();
    if (!"template".equals(request.language()) || !pointer.matches("/channel/[^/]+/(schema|options)(/.*)?")) {
      throw new BusinessException("此字段不是通知渠道模板");
    }
    var email = pointer.startsWith("/channel/email/schema");
    var bindings = new ArrayList<ScriptIntelligenceGlobal>();
    if (email) {
      var schema = request.draft().path("payloadSchema");
      var properties = schema.has("properties") ? schema.path("properties") : schema;
      properties.fields().forEachRemaining(entry -> bindings.add(CodeBindings.binding(entry.getKey(),
          entry.getValue().isObject() ? TypeShape.mapSchema(entry.getValue()) : TypeShape.unknown("payload-field-type"), "notification-payload")));
    }
    return new CodeContextDescription("template", bindings, TypeShape.scalar("string"), List.of(), false,
        email ? Map.of("templateMode", "payload-only", "configurationKeys", ConfigurationKeyCatalog.describe(metadata.application()),
            "embeddedPayloadFields", List.of("titleTemplate", "contentTemplate"))
            : Map.of("templateMode", "config-only", "configurationKeys", ConfigurationKeyCatalog.describe(metadata.application())));
  }
}
