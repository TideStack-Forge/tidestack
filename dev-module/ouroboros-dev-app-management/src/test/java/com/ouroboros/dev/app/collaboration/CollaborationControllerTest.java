package com.ouroboros.dev.app.collaboration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.dev.app.collaboration.CollaborativeDocumentService.*;

class CollaborationControllerTest {
  private CollaborativeDocumentService documents;
  private CollaborationGateway gateway;
  private MockMvc mvc;
  private final Document saved = new Document(CollaborativeDocumentService.FORMAT,
      "overview", 2, "{}", "AAE=", List.of());

  @BeforeEach
  void setUp() {
    documents = mock(CollaborativeDocumentService.class);
    gateway = mock(CollaborationGateway.class);
    mvc = MockMvcBuilders.standaloneSetup(new CollaborationController(documents, gateway)).build();
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "alice")));
  }

  @AfterEach void clear() { CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void readsBootstrapAndInitializesTheDocumentThroughThePublicHttpContract() throws Exception {
    when(documents.list("app", "canvas")).thenReturn(List.of(saved));
    when(gateway.writable("app", "alice")).thenReturn(true);
    mvc.perform(get("/api/dev/app/collaboration/canvas"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.writable").value(true))
        .andExpect(jsonPath("$.data.documents[0].id").value("overview"));
    when(documents.initialize(eq("app"), eq("canvas"), eq("overview"), any(), eq("alice"))).thenReturn(saved);
    mvc.perform(post("/api/dev/app/collaboration/canvas/overview/initialize").contentType(MediaType.APPLICATION_JSON)
        .content("{\"seed\":\"{}\",\"checkpoint\":\"AAE=\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2));
    verify(gateway).requireView("app", "alice");
    verify(gateway).requireEdit("app", "alice");
    verify(gateway).publish("app", "canvas", saved);
  }

  @Test
  void broadcastsOnlyAfterTheOperationHasBeenPersisted() throws Exception {
    when(documents.apply(eq("app"), eq("canvas"), eq("overview"), any(), eq("alice"))).thenReturn(saved);
    mvc.perform(post("/api/dev/app/collaboration/canvas/overview/updates").contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":\"operation\",\"baseRevision\":1,\"data\":\"AAE=\",\"checkpoint\":\"AAE=\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.checkpoint").value("AAE="));
    var order = inOrder(gateway, documents);
    order.verify(gateway).requireEdit("app", "alice");
    order.verify(documents).apply(eq("app"), eq("canvas"), eq("overview"), any(), eq("alice"));
    order.verify(gateway).publish("app", "canvas", saved);
  }

  @Test
  void deniesWritesBeforeTouchingPersistence() {
    doThrow(new BusinessException("denied")).when(gateway).requireEdit("app", "alice");
    assertThrows(Exception.class, () -> mvc.perform(post("/api/dev/app/collaboration/canvas/overview/updates")
        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"operation\",\"baseRevision\":1,\"data\":\"AAE=\",\"checkpoint\":\"AAE=\"}")));
    verifyNoInteractions(documents);
  }
}
