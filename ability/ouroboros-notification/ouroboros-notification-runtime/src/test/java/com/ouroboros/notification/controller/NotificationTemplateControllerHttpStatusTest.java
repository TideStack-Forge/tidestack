package com.ouroboros.notification.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.service.NotificationTemplateRuntimeManagementService;
import com.ouroboros.notification.service.NotificationTemplateService;
import com.ouroboros.notification.service.model.NotificationTemplateManagement.*;
import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationTemplateControllerHttpStatusTest {
  private NotificationTemplateRuntimeManagementService management;
  private NotificationTemplateService persistence;
  private MockMvc mvc;
  private SecurityIdentity identity;
  private static final String ROOT = "/api/sys/notification-template";
  private static final String COMMAND = "{\"expectedDefaultRevision\":\"default:1\",\"expectedOverrideRevision\":\"NONE\",\"changes\":{\"isEnabled\":false}}";

  @BeforeEach
  void setup() {
    management = mock(NotificationTemplateRuntimeManagementService.class);
    persistence = mock(NotificationTemplateService.class);
    identity = mock(SecurityIdentity.class); when(identity.getUserIdentity()).thenReturn("admin");
    var controller = new NotificationTemplateController();
    ReflectionTestUtils.setField(controller, "service", persistence);
    ReflectionTestUtils.setField(controller, "management", management);
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice()).build();
    when(management.getRuntimeDefinitions()).thenReturn(List.of(
        definition("alpha-template", "Alpha", false, true), definition("controlled-template", "Controlled Template", true, true),
        definition("jar-template-alpha", "Jar Alpha", false, false), definition("null-title", null, false, false)));
    when(management.getRuntimeDefinition("alpha-template")).thenReturn(definition("alpha-template", "Alpha", false, true));
    when(management.getRuntimeDefinition("missing")).thenThrow(new BusinessException(404, "通知模板不存在"));
  }

  @Test
  void detailRetainsEffectiveFieldsAndReturnsRuntimeVersionsWithoutCaching() throws Exception {
    mvc.perform(get(ROOT + "/alpha-template")).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.title").value("Alpha"))
        .andExpect(jsonPath("$.data.isControlled").value(false));
    mvc.perform(get(ROOT + "/alpha-template/runtime-definition")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.defaultRevision").value("default:1")).andExpect(jsonPath("$.data.overrideRevision").value("NONE"));
    mvc.perform(get(ROOT + "/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void filtersComposeBeforePaginationAndHandleNullTitles() throws Exception {
    mvc.perform(get(ROOT).param("name", "alpha").param("title", "Alpha").param("page", "0").param("perPage", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("alpha-template"));
    mvc.perform(get(ROOT).param("name", "template").param("title", "t").param("enable", "true").param("controlled", "true")
            .param("source", "OVERRIDE").param("keyword", "Controlled").param("baseChanged", "true"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("controlled-template"));
    mvc.perform(get(ROOT).param("name", "alpha").param("perPage", "0"))
        .andExpect(jsonPath("$.data.items.length()").value(2));
    mvc.perform(get(ROOT).param("page", "2147483647").param("perPage", "2147483647"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    mvc.perform(get(ROOT).param("source", "UNKNOWN")).andExpect(status().isBadRequest());
  }

  @Test
  void newAndRetainedIdWritesShareTheSameCommandAndServerIdentity() throws Exception {
    var result = new WriteResult("alpha-template", true, true, true, "default:1", "id:1", null, null);
    when(management.saveRuntimeOverride(eq("alpha-template"), any(), eq("admin"))).thenReturn(result);
    when(management.restoreDefault(eq("alpha-template"), any(), eq("admin"))).thenReturn(result);
    var row = new NotificationTemplateModel(); row.setName("alpha-template"); when(persistence.getByPrimaryKey("id")).thenReturn(row);
    CurrentSecurityContextHolder.withIdentityThrowing(identity, () -> {
      mvc.perform(put(ROOT + "/alpha-template/runtime-override").contentType("application/json").content(COMMAND))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.committed").value(true));
      mvc.perform(post(ROOT).contentType("application/json").content(COMMAND.replaceFirst("\\{", "{\"name\":\"alpha-template\",")))
          .andExpect(status().isOk());
      mvc.perform(put(ROOT + "/id").contentType("application/json").content(COMMAND)).andExpect(status().isOk());
      mvc.perform(patch(ROOT + "/id").contentType("application/json").content(COMMAND)).andExpect(status().isOk());
      mvc.perform(delete(ROOT + "/id").contentType("application/json").content(COMMAND)).andExpect(status().isOk());
      mvc.perform(post(ROOT + "/alpha-template/restore-default").contentType("application/json").content(COMMAND)).andExpect(status().isOk());
      return null;
    });
    var captor = org.mockito.ArgumentCaptor.forClass(Command.class);
    verify(management, times(4)).saveRuntimeOverride(eq("alpha-template"), captor.capture(), eq("admin"));
    captor.getAllValues().forEach(command -> { assertEquals("default:1", command.expectedDefaultRevision()); assertEquals("NONE", command.expectedOverrideRevision()); });
    verify(persistence, never()).add(any(com.ouroboros.notification.template.NotificationTemplate.class));
  }

  @Test
  void conflictDetailsAndPostCommitUncertaintyArePreservedInErrorData() throws Exception {
    var details = new ErrorDetails("OVERRIDE_CHANGED", "default:2", "id:2", List.of());
    when(management.saveRuntimeOverride(anyString(), any(), anyString())).thenThrow(new ManagementException(409, "版本冲突", details));
    CurrentSecurityContextHolder.withIdentityThrowing(identity, () -> {
      mvc.perform(put(ROOT + "/alpha-template/runtime-override").contentType("application/json").content(COMMAND))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.data.reason").value("OVERRIDE_CHANGED"));
      doReturn(new WriteResult("alpha-template", true, true, false, "default:1", "id:1", null, "COMMITTED_VISIBILITY_UNCONFIRMED"))
          .when(management).saveRuntimeOverride(anyString(), any(), anyString());
      mvc.perform(put(ROOT + "/alpha-template/runtime-override").contentType("application/json").content(COMMAND))
          .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.committed").value(true))
          .andExpect(jsonPath("$.data.overrideRevision").value("id:1"));
      return null;
    });
  }

  @Test
  void unknownEnvelopeFieldsAreRejectedAndValidationDoesNotWrite() throws Exception {
    when(management.validateRuntimeOverride(anyString(), any())).thenReturn(new Validation(true, Map.of(), List.of(), List.of(), List.of(), List.of()));
    mvc.perform(post(ROOT + "/alpha-template/runtime-override/validate").contentType("application/json").content(COMMAND))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
    mvc.perform(post(ROOT + "/alpha-template/runtime-override/validate").contentType("application/json").content(COMMAND.replaceFirst("\\{", "{\"actor\":\"spoofed\",")))
        .andExpect(status().isBadRequest());
    verify(management, never()).saveRuntimeOverride(anyString(), any(), any());
    verifyNoInteractions(persistence);
  }

  private Definition definition(String name, String title, boolean override, boolean enabled) {
    var effective = new java.util.LinkedHashMap<String, Object>(); effective.put("name", name); effective.put("title", title);
    effective.put("channel", Map.of()); effective.put("payloadSchema", Map.of()); effective.put("isEnabled", enabled);
    return new Definition(name, effective, effective, override, true, override ? "OVERRIDE" : "DEFAULT", "jar", "jar", "default:1",
        override ? "id:1" : "NONE", override, override ? "admin" : null, override ? LocalDateTime.now() : null,
        Map.of("view", true, "edit", true, "restore", override), List.of(), List.of());
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) { return WebApiResult.error(exception.getErrorCode(), exception.getMessage()); }
  }
}
