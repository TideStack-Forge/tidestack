package com.ouroboros.notification.email.service.impl;

import java.util.ArrayList;
import java.util.Optional;

import jakarta.mail.Message;
import jakarta.mail.Transport;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.email.adapter.EmailNotificationChannelOptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceImplTest {
  @Test
  void theNextSendUsesChangedHostPortAndCredentials() {
    var configuration = mock(ConfigurationService.class);
    when(configuration.get(String.class, "notification.email.protocol")).thenReturn(Optional.of("smtp"));
    when(configuration.get(String.class, "notification.email.host")).thenReturn(Optional.of("first.invalid"), Optional.of("second.invalid"));
    when(configuration.get(Integer.class, "notification.email.port")).thenReturn(Optional.of(2525), Optional.of(3535));
    when(configuration.get(String.class, "notification.email.username")).thenReturn(Optional.of("first@example.invalid"), Optional.of("second@example.invalid"));
    when(configuration.get(String.class, "notification.email.authorizationCode")).thenReturn(Optional.of("first-test-secret"), Optional.of("second-test-secret"));
    var service = new EmailServiceImpl(); ReflectionTestUtils.setField(service, "configurationService", configuration);
    var messages = new ArrayList<Message>();
    try (var transport = mockStatic(Transport.class)) {
      transport.when(() -> Transport.send(any(Message.class))).thenAnswer(call -> { messages.add(call.getArgument(0)); return null; });
      service.sendEmail("receiver@example.invalid", "first", "body", new EmailNotificationChannelOptions());
      service.sendEmail("receiver@example.invalid", "second", "body", new EmailNotificationChannelOptions());
    }
    assertEquals(2, messages.size());
    var first = messages.get(0).getSession(); var second = messages.get(1).getSession();
    assertEquals("first.invalid", first.getProperty("mail.smtp.host"));
    assertEquals("second.invalid", second.getProperty("mail.smtp.host"));
    assertEquals("3535", second.getProperties().get("mail.smtp.port").toString());
    var authentication = second.requestPasswordAuthentication(null, 3535, "smtp", "test", null);
    assertEquals("second@example.invalid", authentication.getUserName());
    assertEquals("second-test-secret", authentication.getPassword());
    assertNotSame(first, second);
  }
}
