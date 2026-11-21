package com.ouroboros.notification.internal.message.adapter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.notification.internal.message.service.NotificationInternalMessageService;

class InternalMessageNotificationAdapterDelegationTest {

  @Test
  void sendShouldDelegateUnreadCountNotificationToService() {
    NotificationInternalMessageService service = mock(NotificationInternalMessageService.class);

    InternalMessageNotificationAdapter adapter = new InternalMessageNotificationAdapter();
    ReflectionTestUtils.setField(adapter, "service", service);

    InternalMessageNotificationChannelSchema channelSchema = new InternalMessageNotificationChannelSchema();
    channelSchema.setTitleTemplate("title");
    channelSchema.setContentTemplate("content");

    InternalMessageNotificationChannelOptions options = new InternalMessageNotificationChannelOptions();
    options.setPlatform(Collections.singletonMap("web", true));

    var employeeIds = Arrays.asList("emp-1", "emp-2");

    adapter.send(employeeIds, channelSchema, Collections.emptyMap(), options);

    verify(service).sendNotification(eq(employeeIds), eq("title"), eq("content"), same(options));
    verify(service).notifyUnreadCount(eq(employeeIds));
    verifyNoMoreInteractions(service);
  }
  @Test
  void reliableEntryRetainsStableKeyAndUsesTheOriginalTemplateRenderingProtocol() {
    var service = mock(NotificationInternalMessageService.class);
    var adapter = new InternalMessageNotificationAdapter(); ReflectionTestUtils.setField(adapter, "service", service);
    var schema = new InternalMessageNotificationChannelSchema(); schema.setTitleTemplate("title ${name}"); schema.setContentTemplate("body ${name}");
    var options = new InternalMessageNotificationChannelOptions(); options.setPlatform(Collections.singletonMap("web", true));
    var receipt = new com.ouroboros.notification.adapter.NotificationChannelResult(
        com.ouroboros.notification.adapter.NotificationChannelResult.Outcome.ACCEPTED, "persisted");
    org.mockito.Mockito.when(service.sendNotificationWithResult("stable-unit", java.util.List.of("employee"), "title Order", "body Order", options)).thenReturn(receipt);
    org.junit.jupiter.api.Assertions.assertEquals(receipt,
        adapter.sendWithResult("stable-unit", java.util.List.of("employee"), schema, java.util.Map.of("name", "Order"), options).orElseThrow());
    verify(service).sendNotificationWithResult("stable-unit", java.util.List.of("employee"), "title Order", "body Order", options);
    verifyNoMoreInteractions(service);
  }

  @Test
  void invalidReliableConfigurationIsBlockedBeforeEnteringTheMessageService() {
    var service = mock(NotificationInternalMessageService.class);
    var adapter = new InternalMessageNotificationAdapter(); ReflectionTestUtils.setField(adapter, "service", service);
    var receipt = adapter.sendWithResult("stable-unit", java.util.List.of("employee"), null, java.util.Map.of(), null).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(com.ouroboros.notification.adapter.NotificationChannelResult.Outcome.BLOCKED, receipt.outcome());
    verifyNoMoreInteractions(service);
  }

}
