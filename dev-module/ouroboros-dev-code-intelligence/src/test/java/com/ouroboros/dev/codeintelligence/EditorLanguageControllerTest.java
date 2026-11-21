package com.ouroboros.dev.codeintelligence;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EditorLanguageControllerTest {
  private final EditorLanguageSessions sessions = mock(EditorLanguageSessions.class);

  @Test void keepsColdEnvironmentPreparationAsynchronousThenReturnsTheOpenedDocument() throws Exception {
    var pending = new CompletableFuture<EditorLanguageSessions.OpenResult>();
    when(sessions.open(anyString(), anyString(), any())).thenReturn(pending);
    var mvc = MockMvcBuilders.standaloneSetup(new EditorLanguageController(sessions, 120_000)).build();
    var request = mvc.perform(post("/api/dev/app/code-intelligence/sessions").contentType("application/json")
        .content("{\"context\":null,\"text\":\"\"}")).andExpect(request().asyncStarted()).andReturn();
    pending.complete(new EditorLanguageSessions.OpenResult("document", "ouroboros://document", "v1", List.of()));
    mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("document"));
  }

  @Test void closesAnOpenedDocumentIfTheHttpResponseAlreadyExpired() {
    var pending = new CompletableFuture<EditorLanguageSessions.OpenResult>();
    when(sessions.open(anyString(), anyString(), any())).thenReturn(pending);
    when(sessions.close(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(List.of()));
    var controller = new EditorLanguageController(sessions, 120_000);
    var request = new MockHttpServletRequest();
    var response = controller.open("app", null, request);
    response.setErrorResult(new IllegalStateException("request expired"));
    pending.complete(new EditorLanguageSessions.OpenResult("document", "ouroboros://document", "v1", List.of()));
    verify(sessions).close("app", request.getSession().getId(), "document");
  }
}
