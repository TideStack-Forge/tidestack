package com.ouroboros.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.adapter.NotificationChannelResult;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDispatchContractTest {
  @Test
  void stableSendingIdentityIsRequiredAndRecipientCollectionCannotChangeAfterConstruction() {
    var employees = new ArrayList<>(List.of("employee"));
    var request = new NotificationDispatchRequest("fact", "rule", "revision", employees, "template", Map.of(), null);
    employees.clear(); assertEquals(List.of("employee"), request.employeeIds());
    assertThrows(UnsupportedOperationException.class, request.employeeIds()::clear);
    assertThrows(IllegalArgumentException.class, () -> new NotificationDispatchRequest("", "rule", "revision", List.of(), "template", Map.of(), null));
    assertThrows(IllegalArgumentException.class, () -> new NotificationDispatchRequest("fact", "", "revision", List.of(), "template", Map.of(), null));
    assertThrows(IllegalArgumentException.class, () -> new NotificationDispatchRequest("fact", "rule", "", List.of(), "template", Map.of(), null));
  }

  @Test
  void policyAndChannelOutcomesRequireExplicitExplanationsAndResultUnitsAreImmutable() {
    assertThrows(NullPointerException.class, () -> new NotificationDeliveryPolicy.Decision(null, "reason"));
    assertThrows(IllegalArgumentException.class, () -> new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.BLOCK, ""));
    assertThrows(NullPointerException.class, () -> new NotificationChannelResult(null, "reason"));
    assertThrows(IllegalArgumentException.class, () -> new NotificationChannelResult(NotificationChannelResult.Outcome.UNKNOWN, ""));
    var result = new NotificationDispatchResult(null, NotificationDispatchResult.Status.BLOCKED, "missing-template", List.of());
    assertFalse(result.isComplete()); assertThrows(UnsupportedOperationException.class, result.units()::clear);
  }

  @Test
  void anExistingDispatcherCannotAccidentallyClaimReliableDeliveryByCallingItsVoidMethod() {
    var called = new AtomicBoolean();
    EmployeeNotificationDispatcher legacy = (employees, template, payload, options) -> called.set(true);
    var request = new NotificationDispatchRequest("fact", "rule", "revision", List.of("employee"), "template", Map.of(), null);
    assertThrows(BusinessException.class, () -> legacy.sendWithResult(request, (employee, channel, addresses) ->
        new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.ALLOW, "allowed")));
    assertFalse(called.get());
  }
}
