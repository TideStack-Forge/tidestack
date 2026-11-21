package com.ouroboros.documentmanagement.webapp.controller;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryManagementFacade;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentLibraryManagementControllerHttpStatusTest {

  @Test
  void missingLibrariesShouldReturnHttp404() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a", "ROLE_USER"));
    try {
      DocumentLibraryManagementFacade facade = mock(DocumentLibraryManagementFacade.class);
      DocumentLibraryContentFacade contentFacade = mock(DocumentLibraryContentFacade.class);
      when(facade.getLibrary(anyString(), anyString())).thenReturn(Optional.empty());
      when(facade.getLibraryByName(anyString(), anyString())).thenReturn(Optional.empty());

      var mockMvc = MockMvcBuilders.standaloneSetup(new DocumentLibraryManagementController(facade, contentFacade))
          .setControllerAdvice(new WebApiResultHttpStatusAdvice())
          .build();

      mockMvc.perform(get("/api/document-libraries/{libraryId}", "missing"))
          .andExpect(status().isNotFound());

      mockMvc.perform(get("/api/document-libraries/by-name/{name}", "missing"))
          .andExpect(status().isNotFound());

      mockMvc.perform(get("/api/document-libraries/{libraryId}/overview", "missing"))
          .andExpect(status().isNotFound());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
