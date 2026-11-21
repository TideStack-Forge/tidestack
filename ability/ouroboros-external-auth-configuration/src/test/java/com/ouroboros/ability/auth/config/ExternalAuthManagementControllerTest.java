package com.ouroboros.ability.auth.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.util.Json;

class ExternalAuthManagementControllerTest {
  @Test
  void readsAndSavesTheAuthorizedGroupWithItsCasRevisionAndNoOtherProtocolData() {
    var service = mock(ConfigurationManagementService.class);
    var controller = new ExternalAuthManagementController(service);
    var snapshot = new ConfigurationSnapshot(UnifiedExternalAuthConfiguration.KEY,
        Map.of("ldap", List.of(Map.of("key", "corp")), "cas", List.of(Map.of("key", "other"))), null, "revision-2", new ConfigurationMetadata());
    when(service.readManagementSnapshot(UnifiedExternalAuthConfiguration.KEY)).thenReturn(Optional.of(snapshot));
    when(service.mutateManagementConfigurations(anyMap(), eq(Map.of(UnifiedExternalAuthConfiguration.KEY, "revision-1"))))
        .thenReturn(Map.of(UnifiedExternalAuthConfiguration.KEY, snapshot));
    try (var access = mockStatic(ExternalAuthManagementAccess.class)) {
      access.when(() -> ExternalAuthManagementAccess.authority("ldap")).thenReturn(ExternalAuthManagementAccess.CONFIGURE);
      assertThat(Json.toJsonString(controller.read("ldap").getData())).contains("corp", "revision-2").doesNotContain("other");
      var request = new ExternalAuthManagementController.SaveRequest("revision-1", Map.of("providers", List.of()));
      assertThat(Json.toJsonString(controller.save("ldap", request).getData())).contains("revision-2");
      assertThatThrownBy(() -> controller.save("ldap", new ExternalAuthManagementController.SaveRequest(null, request.changes())))
          .hasMessageContaining("版本");
    }
    var response = new MockHttpServletResponse(); controller.preventCaching(response);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void deniedAccessNeverReadsOrWritesConfiguration() {
    var service = mock(ConfigurationManagementService.class);
    var controller = new ExternalAuthManagementController(service);
    assertThatThrownBy(() -> controller.read("ldap")).hasMessageContaining("无权");
    assertThatThrownBy(() -> controller.save("ldap", new ExternalAuthManagementController.SaveRequest("revision", Map.of())))
        .hasMessageContaining("无权");
    verifyNoInteractions(service);
  }
}
