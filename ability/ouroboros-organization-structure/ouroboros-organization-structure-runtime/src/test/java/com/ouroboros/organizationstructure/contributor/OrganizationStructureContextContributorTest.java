package com.ouroboros.organizationstructure.contributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import com.ouroboros.expression.ExpressionContextContributor;
import com.ouroboros.script.ScriptContextContributor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationStructureContextContributorTest {

  @Test
  void shouldDiscoverContributorFromSpi() {
    assertServiceDiscoverable(ScriptContextContributor.class, OrganizationStructureContextContributor.class);
    assertServiceDiscoverable(ExpressionContextContributor.class, OrganizationStructureContextContributor.class);
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

  @Test
  void shouldExposeOrganizationContextWithStableScriptKeys() throws Exception {
    Class<?> contributorClass = assertDoesNotThrow(
        () -> Class.forName("com.ouroboros.organizationstructure.contributor.OrganizationStructureContextContributor"));
    Class<?> scriptContextClass = assertDoesNotThrow(
        () -> Class.forName("com.ouroboros.organizationstructure.contributor.OrganizationScriptContext"));

    Object contributor = contributorClass.getDeclaredConstructor().newInstance();

    @SuppressWarnings("unchecked")
    Map<String, Object> values = (Map<String, Object>) contributorClass.getMethod("getBindings").invoke(contributor);

    assertTrue(values.containsKey("OrganizationHolder"));
    assertTrue(values.containsKey("OrganizationContext"));

    Object holder = values.get("OrganizationHolder");
    Object context = values.get("OrganizationContext");
    assertSame(holder, context);
    assertTrue(scriptContextClass.isInstance(holder));
    assertNull(scriptContextClass.getMethod("getEmployeeId").invoke(holder));
    assertNull(scriptContextClass.getMethod("getDepartmentId").invoke(holder));
    assertNull(scriptContextClass.getMethod("getCompanyId").invoke(holder));
  }

}
