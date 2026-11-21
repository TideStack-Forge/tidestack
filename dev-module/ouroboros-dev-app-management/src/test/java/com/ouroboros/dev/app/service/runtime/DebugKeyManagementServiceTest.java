package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class DebugKeyManagementServiceTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(DebugKeyManagementService.class);

  private ListAppender<ILoggingEvent> logAppender;

  @AfterEach
  void tearDown() {
    if (logAppender != null) {
      logger.detachAppender(logAppender);
      logAppender.stop();
      logAppender = null;
    }
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldLogSuccessForGenerateAndDeleteWithoutFullDebugKey() throws Exception {
    DebugKeyManagementService service = createService("alice");
    logAppender = attachLogAppender();

    DebugKeyMaterial generated = service.generateDebugKey("crm", "alice", "integration", 7);
    assertNotNull(generated.getDebugKey());
    assertTrue(generated.getDebugKey().matches("[0-9a-f]{32}"));
    assertEquals("crm", generated.getAppName());

    List<ILoggingEvent> infoEvents = events(Level.INFO);
    assertEquals(1, infoEvents.size());
    assertTrue(infoEvents.get(0).getFormattedMessage().contains("action=GENERATE"));
    assertTrue(infoEvents.get(0).getFormattedMessage().contains("userIdentity=alice"));
    assertTrue(infoEvents.get(0).getFormattedMessage().contains("profileId=alice"));
    assertTrue(infoEvents.get(0).getFormattedMessage().contains("operator=alice"));
    assertTrue(infoEvents.get(0).getFormattedMessage().contains("result=SUCCESS"));
    assertTrue(infoEvents.get(0).getFormattedMessage().contains("keyPrefix=" + generated.getDebugKey().substring(0, 8)));
    assertFalse(infoEvents.get(0).getFormattedMessage().contains(generated.getDebugKey()));

    service.deleteDebugKey("crm", "alice");
    List<ILoggingEvent> allInfoEvents = events(Level.INFO);
    assertEquals(2, allInfoEvents.size());
    ILoggingEvent deleteEvent = allInfoEvents.get(1);
    assertTrue(deleteEvent.getFormattedMessage().contains("action=DELETE"));
    assertTrue(deleteEvent.getFormattedMessage().contains("result=SUCCESS"));
    assertTrue(deleteEvent.getFormattedMessage().contains("keyPrefix=" + generated.getDebugKey().substring(0, 8)));
  }

  @Test
  void shouldLogWarningForUnauthorizedGenerateAndKeepKeyMasked() throws Exception {
    DebugKeyManagementService service = createService("bob");
    logAppender = attachLogAppender();

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.generateDebugKey("crm", "alice", "denied", 7));
    assertTrue(exception.getMessage().contains("无权访问用户 Dev Key"));

    List<ILoggingEvent> warnEvents = events(Level.WARN);
    assertEquals(1, warnEvents.size());
    ILoggingEvent event = warnEvents.get(0);
    assertTrue(event.getFormattedMessage().contains("action=GENERATE"));
    assertTrue(event.getFormattedMessage().contains("userIdentity=alice"));
    assertTrue(event.getFormattedMessage().contains("profileId=alice"));
    assertTrue(event.getFormattedMessage().contains("operator=bob"));
    assertTrue(event.getFormattedMessage().contains("result=FAILURE"));
    assertTrue(event.getFormattedMessage().contains("keyPrefix=null"));
    assertNotNull(event.getThrowableProxy());
  }

  @Test
  void shouldLogSuccessForRotateWithMaskedPrefixOnly() throws Exception {
    DebugKeyManagementService service = createService("alice");
    logAppender = attachLogAppender();

    DebugKeyMaterial generated = service.generateDebugKey("crm", "alice", "initial", 7);
    service.rotateDebugKey("crm", "alice", "rotated", 14);

    List<ILoggingEvent> infoEvents = events(Level.INFO);
    assertEquals(2, infoEvents.size());
    ILoggingEvent rotateEvent = infoEvents.get(1);
    assertTrue(rotateEvent.getFormattedMessage().contains("action=ROTATE"));
    assertTrue(rotateEvent.getFormattedMessage().contains("result=SUCCESS"));
    assertTrue(rotateEvent.getFormattedMessage().contains("keyPrefix="));
    assertFalse(rotateEvent.getFormattedMessage().contains(generated.getDebugKey()));
  }

  private DebugKeyManagementService createService(String principal) {
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(OuroborosAuthenticationHolder.adapt(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList())));
      }
    });

    DevKeyStore store = new DevKeyStore(new InMemoryDevKeyDataModel());
    DebugKeyGenerationService generationService = new DebugKeyGenerationService(Duration.ofDays(90));
    AuthenticationPrincipalTestAppProfileResolver resolver = new AuthenticationPrincipalTestAppProfileResolver();
    return new DebugKeyManagementService(generationService, store, resolver);
  }

  private ListAppender<ILoggingEvent> attachLogAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private List<ILoggingEvent> events(Level level) {
    return logAppender.list.stream()
        .filter(event -> event.getLevel().equals(level))
        .collect(java.util.stream.Collectors.toList());
  }

}
