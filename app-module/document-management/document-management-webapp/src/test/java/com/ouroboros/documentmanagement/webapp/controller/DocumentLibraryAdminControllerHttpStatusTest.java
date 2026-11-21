package com.ouroboros.documentmanagement.webapp.controller;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentLibraryAdminControllerHttpStatusTest {

  @Test
  void missingLibrariesShouldReturnHttp404() throws Exception {
    DocumentLibraryAdminFacade facade = mock(DocumentLibraryAdminFacade.class);
    when(facade.getLibrary(anyString())).thenReturn(Optional.empty());
    when(facade.getLibraryByName(anyString())).thenReturn(Optional.empty());

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DocumentLibraryAdminQueryController(facade))
        .setControllerAdvice(new WebApiResultHttpStatusAdvice())
        .build();

    MockHttpServletResponse byId = mockMvc.perform(get("/api/document-management/admin/libraries/{libraryId}", "missing"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse();
    assertEquals(404, responseCode(byId.getContentAsString(StandardCharsets.UTF_8)));

    MockHttpServletResponse byName = mockMvc.perform(get("/api/document-management/admin/libraries/by-name/{name}", "missing"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse();
    assertEquals(404, responseCode(byName.getContentAsString(StandardCharsets.UTF_8)));
  }

  private int responseCode(String body) {
    return ((Number) Json.toMap(body).get("code")).intValue();
  }
}
