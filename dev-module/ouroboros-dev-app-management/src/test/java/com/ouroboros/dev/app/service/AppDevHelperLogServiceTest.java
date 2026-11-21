package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.websocket.StompCompensationRegistrar;

class AppDevHelperLogServiceTest {

  private AppDevHelperLogService service;
  private StompCompensationRegistrar.UserTopicChannel helperLogsChannel;

  @BeforeEach
  void setUp() {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.<Integer>get(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.of(Integer.valueOf(20)));
    helperLogsChannel = mock(StompCompensationRegistrar.UserTopicChannel.class);
    service = new AppDevHelperLogService(new AppLogCacheService(configurationService));
    service.bindChannel(helperLogsChannel);
  }

  @Test
  void shouldCacheSystemAndRuntimeLogsForHelperReplay() {
    service.appendSystemLog("crm", "starting helper");
    service.appendCommandResult("crm", new CommandResult()
        .setAppName("crm")
        .setCommand("refreshMetadata")
        .setRuntimeGeneration(Long.valueOf(3L))
        .setSuccess(true)
        .setMessage("metadata refreshed"));

    List<String> logs = service.getCachedLogs("crm");

    assertEquals(2, logs.size());
    assertTrue(logs.get(0).contains("[SYSTEM] starting helper"));
    assertTrue(logs.get(1).contains("[RUNTIME] SUCCESS refreshMetadata generation=3 metadata refreshed"));
  }

  @Test
  void shouldStreamHelperLogsToRegisteredSubscribers() {
    service.registerSubscriber("crm", "alice");

    service.appendSystemLog("crm", "starting helper");

    verify(helperLogsChannel).sendToUser(
        eq("alice"),
        eq(java.util.Collections.singletonMap("appName", "crm")),
        org.mockito.ArgumentMatchers.contains("[SYSTEM] starting helper")
    );
  }

  @Test
  void shouldClearCachedLogsAndSubscribers() {
    service.registerSubscriber("crm", "alice");
    service.appendSystemLog("crm", "old helper");

    service.clear("crm");
    service.appendSystemLog("crm", "new helper");

    List<String> logs = service.getCachedLogs("crm");
    assertEquals(1, logs.size());
    assertTrue(logs.get(0).contains("[SYSTEM] new helper"));
    assertFalse(logs.get(0).contains("old helper"));
    verify(helperLogsChannel, never()).sendToUser(
        eq("alice"),
        eq(java.util.Collections.singletonMap("appName", "crm")),
        org.mockito.ArgumentMatchers.contains("[SYSTEM] new helper")
    );
  }

  @Test
  void shouldAppendOnlyNewRuntimeOutputLines() {
    service.appendRuntimeOutput("crm", java.util.Arrays.asList("assistant started", "index ready"));
    service.appendRuntimeOutput("crm", java.util.Arrays.asList("assistant started", "index ready", "metadata refreshed"));

    List<String> logs = service.getCachedLogs("crm");

    assertEquals(2, logs.size());
    assertEquals("assistant started\nindex ready\n", logs.get(0));
    assertEquals("metadata refreshed\n", logs.get(1));
    assertFalse(logs.get(0).contains("[CONTAINER]"));
    assertFalse(logs.get(1).contains("[CONTAINER]"));
    assertEquals(
        java.util.Collections.singletonList("assistant started\nindex ready\nmetadata refreshed\n"),
        service.getReplayPayloads("crm")
    );
  }

  @Test
  void shouldKeepExistingSystemAndRuntimeLogsWhenBackfillingContainerOutput() {
    service.appendSystemLog("crm", "starting helper");
    service.appendCommandResult("crm", new CommandResult()
        .setAppName("crm")
        .setCommand("refreshMetadata")
        .setSuccess(true)
        .setMessage("metadata refreshed"));

    service.appendRuntimeOutput("crm", java.util.Arrays.asList("assistant started", "index ready"));

    List<String> logs = service.getCachedLogs("crm");

    assertEquals(3, logs.size());
    assertTrue(logs.get(0).contains("[SYSTEM] starting helper"));
    assertTrue(logs.get(1).contains("[RUNTIME] SUCCESS refreshMetadata metadata refreshed"));
    assertEquals("assistant started\nindex ready\n", logs.get(2));
    assertFalse(logs.get(2).contains("[CONTAINER]"));
  }

  @Test
  void shouldStreamRuntimeOutputAsSingleChunkToRegisteredSubscribers() {
    service.registerSubscriber("crm", "alice");

    service.appendRuntimeOutput("crm", Arrays.asList("assistant started", "index ready"));

    verify(helperLogsChannel).sendToUser(
        eq("alice"),
        eq(java.util.Collections.singletonMap("appName", "crm")),
        eq("assistant started\nindex ready\n")
    );
  }

  @Test
  void shouldResetRuntimeSnapshotWithoutDroppingSubscribers() {
    service.registerSubscriber("crm", "alice");
    service.appendRuntimeOutput("crm", Arrays.asList("old boot", "old ready"));

    service.resetRuntimeOutput("crm");
    service.appendRuntimeOutput("crm", Arrays.asList("new boot", "new ready"));

    List<String> logs = service.getCachedLogs("crm");
    assertEquals(2, logs.size());
    assertEquals("old boot\nold ready\n", logs.get(0));
    assertEquals("new boot\nnew ready\n", logs.get(1));
    verify(helperLogsChannel).sendToUser(
        eq("alice"),
        eq(java.util.Collections.singletonMap("appName", "crm")),
        eq("new boot\nnew ready\n")
    );
  }
}
