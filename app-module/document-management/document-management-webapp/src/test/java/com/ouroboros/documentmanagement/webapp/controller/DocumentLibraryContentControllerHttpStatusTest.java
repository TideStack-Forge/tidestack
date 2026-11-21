package com.ouroboros.documentmanagement.webapp.controller;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentLibraryContentControllerHttpStatusTest {

  @Test
  void missingResourcesShouldReturnHttp404() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a", "ROLE_USER"));
    try {
      DocumentLibraryContentFacade facade = mock(DocumentLibraryContentFacade.class);
      when(facade.getDirectoryTree(anyString(), anyString())).thenReturn(Optional.empty());
      when(facade.getRootDirectory(anyString(), anyString())).thenReturn(Optional.empty());
      when(facade.getDocument(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
      when(facade.getDirectory(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

      MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
              new DocumentLibraryNavigationController(facade),
              new DocumentFileQueryController(facade),
              new DocumentFileOperationController(facade),
              new DocumentNodeManagementController(facade),
              new DocumentPermissionController(facade))
          .setControllerAdvice(new WebApiResultHttpStatusAdvice())
          .build();

      MockHttpServletResponse tree = mockMvc.perform(get("/api/document-libraries/{libraryId}/directories", "missing"))
          .andExpect(status().isNotFound())
          .andReturn()
          .getResponse();
      assertEquals(404, responseCode(tree.getContentAsString(StandardCharsets.UTF_8)));

      MockHttpServletResponse root = mockMvc.perform(get("/api/document-libraries/{libraryId}/directories", "missing")
              .param("buildTree", "false"))
          .andExpect(status().isNotFound())
          .andReturn()
          .getResponse();
      assertEquals(404, responseCode(root.getContentAsString(StandardCharsets.UTF_8)));

      MockHttpServletResponse document = mockMvc.perform(
              get("/api/document-libraries/{libraryId}/documents/{documentId}", "missing", "doc"))
          .andExpect(status().isNotFound())
          .andReturn()
          .getResponse();
      assertEquals(404, responseCode(document.getContentAsString(StandardCharsets.UTF_8)));

      MockHttpServletResponse directory = mockMvc.perform(
              get("/api/document-libraries/{libraryId}/directories/{directoryId}", "missing", "dir"))
          .andExpect(status().isNotFound())
          .andReturn()
          .getResponse();
      assertEquals(404, responseCode(directory.getContentAsString(StandardCharsets.UTF_8)));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private int responseCode(String body) {
    return ((Number) Json.toMap(body).get("code")).intValue();
  }
}
