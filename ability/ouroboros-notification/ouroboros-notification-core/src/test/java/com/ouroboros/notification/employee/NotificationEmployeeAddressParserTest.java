package com.ouroboros.notification.employee;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NotificationEmployeeAddressParserTest {
  @Test void theOriginalParserRetainsEachEmployeesAddressesAndFrozenChannelOptions() {
    NotificationEmployeeAddressParser parser = (employees, channels) -> {
      assertThat(employees).hasSize(1);
      return Map.of("email", List.of(employees.iterator().next() + "@example.test", "shared@example.test"));
    };
    var options = new LinkedHashMap<String, Object>(); options.put("connectionId", "smtp-main"); options.put("optional", null);
    var resolution = parser.resolve(List.of("alice", "bob"), List.of("email"), Map.of("email", options)).get("email");
    options.put("connectionId", "changed");
    assertThat(resolution.channelOptions()).containsEntry("connectionId", "smtp-main");
    assertThat(resolution.addresses().get("alice")).containsExactly("alice@example.test", "shared@example.test");
    assertThat(resolution.addresses().get("bob")).containsExactly("bob@example.test", "shared@example.test");
    assertThatThrownBy(() -> resolution.addresses().get("alice").clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test void missingLegacyAddressesAreEmptyAndCannotBecomeADefaultRecipient() {
    NotificationEmployeeAddressParser parser = (employees, channels) -> Map.of();
    var resolution = parser.resolve(List.of("alice"), List.of("unknown"), Map.of()).get("unknown");
    assertThat(resolution.addresses()).containsEntry("alice", List.of());
    assertThat(resolution.channelOptions()).isEmpty();
  }
}
