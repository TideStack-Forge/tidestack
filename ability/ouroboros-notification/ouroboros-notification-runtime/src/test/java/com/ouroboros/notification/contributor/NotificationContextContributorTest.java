package com.ouroboros.notification.contributor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.notification.AddressNotificationDispatcher;
import com.ouroboros.notification.DispatchOptions;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.script.ScriptContextContributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationContextContributorTest {

  @AfterEach
  void clearProxyDelegate() throws Exception {
    Field delegateField = NotificationContextContributor.Proxy.class.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    delegateField.set(null, null);
  }

  @Test
  void proxyShouldExposeNotificationDispatcherAndForwardAddressCalls() {
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    NotificationContextContributor contributor = new NotificationContextContributor();
    ReflectionTestUtils.setField(contributor, "addressNotificationDispatcher", addressDispatcher);
    ReflectionTestUtils.setField(contributor, "employeeNotificationDispatcher", employeeDispatcher);

    NotificationContextContributor.Proxy proxy = new NotificationContextContributor.Proxy();
    Map<String, Object> bindings = proxy.getBindings();
    DispatchOptions options = new DispatchOptions();
    options.setSendByEmployee("emp-1");
    Map<String, Collection<String>> addresses = Collections.singletonMap("email", Collections.singletonList("test@example.com"));
    Map<String, Object> payload = Collections.singletonMap("message", "hello");

    assertNotNull(bindings.get("NotificationDispatcher"));
    assertSameClass(proxy, bindings.get("NotificationDispatcher"));

    proxy.sendByAddress(addresses, "tpl", payload);
    proxy.sendByAddress(addresses, "tpl", payload, options);

    verify(addressDispatcher).send(addresses, "tpl", payload, null);
    verify(addressDispatcher).send(addresses, "tpl", payload, options);
  }

  @Test
  void proxyShouldForwardEmployeeCallsForSingleAndCollectionOverloads() {
    AddressNotificationDispatcher addressDispatcher = mock(AddressNotificationDispatcher.class);
    EmployeeNotificationDispatcher employeeDispatcher = mock(EmployeeNotificationDispatcher.class);
    NotificationContextContributor contributor = new NotificationContextContributor();
    ReflectionTestUtils.setField(contributor, "addressNotificationDispatcher", addressDispatcher);
    ReflectionTestUtils.setField(contributor, "employeeNotificationDispatcher", employeeDispatcher);

    NotificationContextContributor.Proxy proxy = new NotificationContextContributor.Proxy();
    DispatchOptions options = new DispatchOptions();
    options.setSendByEmployee("emp-1");
    Map<String, Object> payload = Collections.singletonMap("message", "hello");
    List<String> employeeIds = Collections.singletonList("emp-2");

    proxy.sendByEmployee("emp-2", "tpl", payload);
    proxy.sendByEmployee("emp-2", "tpl", payload, options);
    proxy.sendByEmployee(employeeIds, "tpl", payload);
    proxy.sendByEmployee(employeeIds, "tpl", payload, options);

    verify(employeeDispatcher).send("emp-2", "tpl", payload);
    verify(employeeDispatcher).send("emp-2", "tpl", payload, options);
    verify(employeeDispatcher).send(employeeIds, "tpl", payload);
    verify(employeeDispatcher).send(employeeIds, "tpl", payload, options);
  }

  @Test
  void scriptContextContributorSpiShouldRegisterNotificationProxy() {
    assertServiceDiscoverable(ScriptContextContributor.class, NotificationContextContributor.Proxy.class);
  }

  private void assertSameClass(Object expected, Object actual) {
    assertEquals(expected.getClass(), actual.getClass());
  }

  private <T> void assertServiceDiscoverable(Class<T> serviceType, Class<? extends T> expectedProvider) {
    List<String> discoveredProviders = new ArrayList<>();
    for (T provider : ServiceLoader.load(serviceType)) {
      discoveredProviders.add(provider.getClass().getName());
      if (expectedProvider.isInstance(provider)) {
        return;
      }
    }

    fail("Provider " + expectedProvider.getName()
        + " not found for " + serviceType.getName()
        + ". Discovered: " + discoveredProviders);
  }
}
