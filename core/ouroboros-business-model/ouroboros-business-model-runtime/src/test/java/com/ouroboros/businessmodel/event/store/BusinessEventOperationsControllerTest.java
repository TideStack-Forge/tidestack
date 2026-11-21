package com.ouroboros.businessmodel.event.store;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessEventOperationsControllerTest {
  @Test
  void diagnosticsAndRedriveKeepThePlatformResponseEnvelope() throws Exception {
    var operations = mock(BusinessEventOperations.class);
    when(operations.inspect("event-1")).thenReturn(new BusinessEventOperations.Diagnostics(Optional.empty(), List.of(
        new BusinessEventOperations.Delivery("delivery-1", "inventory.Stock", "reserve", "PRECONDITION_BLOCKED",
            1, 0, "target-action-precondition-unsatisfied", null, "matched", "attempt", "target", "handler", "v1",
            "AFTER_COMMIT_TRANSACTIONAL", "event-service")), false));
    when(operations.redrive("delivery-1")).thenReturn(true);
    var http = MockMvcBuilders.standaloneSetup(new BusinessEventOperationsController(operations)).build();
    http.perform(get("/api/business-events/event-1/deliveries"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.deliveries[0].status").value("PRECONDITION_BLOCKED"))
        .andExpect(jsonPath("$.data.deliveries[0].diagnosticCode").value("target-action-precondition-unsatisfied"))
        .andExpect(jsonPath("$.data.deliveries[0].user").value("event-service"))
        .andExpect(jsonPath("$.data.deliveries[0].executorRef").doesNotExist())
        .andExpect(jsonPath("$.data.canRedrive").value(false))
        .andExpect(jsonPath("$.data.deliveries[0].payload").doesNotExist());
    http.perform(post("/api/business-event-deliveries/delivery-1/redrive"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(true));
    verify(operations).inspect("event-1");
    verify(operations).redrive("delivery-1");
  }
}
