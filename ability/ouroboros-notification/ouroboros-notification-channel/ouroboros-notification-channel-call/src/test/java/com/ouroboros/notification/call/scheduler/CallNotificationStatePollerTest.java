package com.ouroboros.notification.call.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ouroboros.notification.call.enums.CallState;

class CallNotificationStatePollerTest {

  @Test
  void connectedPublishesImmediately() {
    assertTrue(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.CONNECTED, LocalDateTime.now(), LocalDateTime.now()));
  }

  @Test
  void notConnectedWaitsOneMinute() {
    LocalDateTime now = LocalDateTime.now();
    assertFalse(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.NOT_CONNECTED, now, now));
    assertTrue(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.NOT_CONNECTED, now.minusMinutes(2), now));
  }

  @Test
  void failWaitsOneMinute() {
    LocalDateTime now = LocalDateTime.now();
    assertFalse(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.FAIL, now, now));
    assertTrue(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.FAIL, now.minusMinutes(2), now));
  }

  @Test
  void unfinishedStatesAreIgnored() {
    LocalDateTime now = LocalDateTime.now();
    assertFalse(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.PENDING, now.minusMinutes(2), now));
    assertFalse(CallNotificationStatePoller.shouldPublishStateChangedEvent(
        CallState.UNKNOWN, now.minusMinutes(2), now));
    assertFalse(CallNotificationStatePoller.shouldPublishStateChangedEvent(null, now, now));
  }
}
