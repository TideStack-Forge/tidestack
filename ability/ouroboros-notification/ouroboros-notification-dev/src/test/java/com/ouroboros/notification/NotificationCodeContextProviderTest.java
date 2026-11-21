package com.ouroboros.notification;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class NotificationCodeContextProviderTest {
  @Test void emailProvidesOnlyTopLevelPayloadKeysAndOtherChannelsRemainConfigOnly() {
    var provider = new NotificationCodeContextProvider();
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.unavailable(), new ScriptIntelligenceSnapshot());
    var email = provider.describe(request("/channel/email/schema"), view);
    assertFalse(email.allowGlobals());
    assertEquals(List.of("orderId"), email.bindings().stream().map(binding -> binding.getName()).toList());
    assertEquals("payload-only", email.projectionHints().get("templateMode"));
    assertEquals("config-only", provider.describe(request("/channel/wechat/options"), view).projectionHints().get("templateMode"));
    assertTrue(provider.describe(request("/channel/wechat/schema"), view).bindings().isEmpty());
    assertThrows(BusinessException.class, () -> provider.describe(request("/name"), view));
  }
  private CodeContextRequest request(String pointer) {
    return new CodeContextRequest("notification-editor", 1, 1, "notification.template", "template", new CodeContextRequest.Resource("notification", "order"),
        new CodeContextRequest.Location(null, pointer), Json.toBean(JsonNode.class, "{\"payloadSchema\":{\"orderId\":{\"type\":\"string\"}}}"));
  }
}
