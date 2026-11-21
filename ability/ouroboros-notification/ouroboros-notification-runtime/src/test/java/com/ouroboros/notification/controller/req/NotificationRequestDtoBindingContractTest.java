package com.ouroboros.notification.controller.req;

import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.notification.DispatchOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRequestDtoBindingContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void sendNotificationReqShouldBindTemplateTargetsPayloadAndDispatchOptions() throws Exception {
    String json = "{"
        + "\"template\":\"welcome\"," 
        + "\"employeeIds\":[\"emp-1\",\"emp-2\"]," 
        + "\"addresses\":{\"email\":[\"a@example.com\"]}," 
        + "\"payload\":{\"message\":\"hello\"}," 
        + "\"dispatchOptions\":{"
        + "\"channels\":[\"test-channel\"],"
        + "\"channelOptions\":{\"test-channel\":{}},"
        + "\"sendByEmployee\":\"emp-0\""
        + "}"
        + "}";

    SendNotificationReq req = objectMapper.readValue(json, SendNotificationReq.class);

    assertEquals("welcome", req.getTemplate());
    assertEquals(2, req.getEmployeeIds().size());
    assertEquals("emp-1", req.getEmployeeIds().get(0));
    assertEquals("a@example.com", req.getAddresses().get("email").iterator().next());
    assertEquals("hello", req.getPayload().get("message"));
    DispatchOptions options = req.getDispatchOptions();
    assertNotNull(options);
    assertEquals("emp-0", options.getSendByEmployee());
    assertTrue(options.getChannels().contains("test-channel"));
    assertNotNull(options.getChannelOptions().get("test-channel"));
  }

  @Test
  void channelBindingReqShouldRoundTripAddressAndRemarks() throws Exception {
    ChannelBindingReq req = new ChannelBindingReq();
    req.setAddress("user@example.com");
    req.setRemarks("home");

    String json = objectMapper.writeValueAsString(req);
    ChannelBindingReq decoded = objectMapper.readValue(json, ChannelBindingReq.class);

    assertEquals("user@example.com", decoded.getAddress());
    assertEquals("home", decoded.getRemarks());
  }
}
