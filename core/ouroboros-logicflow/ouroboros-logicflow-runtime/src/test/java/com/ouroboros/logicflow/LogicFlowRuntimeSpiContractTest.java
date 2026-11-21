package com.ouroboros.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceLoader;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.model.FlowModelMetaProvider;
import com.ouroboros.logicflow.model.FlowModelProvider;

class LogicFlowRuntimeSpiContractTest {

  @Test
  void shouldDiscoverRuntimeProvidersFromSpi() {
    assertServiceDiscoverable(FlowModelMetaProvider.class, JarLogicModelMetaProvider.class);
    assertServiceDiscoverable(FlowModelMetaProvider.class, AppLogicModelMetaProvider.SingletonHolder.class);
    assertServiceDiscoverable(FlowModelProvider.class, DefaultLogicFlowProvider.SingletonHolder.class);
  }

  @Test
  void shouldUseRuntimeDescriptorNames() throws Exception {
    List<String> metaProviders = readProviderEntries(readDescriptorLines(
        "META-INF/services/com.ouroboros.logicflow.model.FlowModelMetaProvider"));
    List<String> modelProviders = readProviderEntries(readDescriptorLines(
        "META-INF/services/com.ouroboros.logicflow.model.FlowModelProvider"));

    assertTrue(metaProviders.contains("com.ouroboros.logicflow.JarLogicModelMetaProvider"));
    assertTrue(metaProviders.contains("com.ouroboros.logicflow.AppLogicModelMetaProvider$SingletonHolder"));
    assertTrue(modelProviders.contains("com.ouroboros.logicflow.DefaultLogicFlowProvider$SingletonHolder"));
  }

  @Test
  void shouldKeepCurrentProviderPriorityBridgeContract() {
    assertPriority(JarLogicModelMetaProvider.class, 0);
    assertPriority(AppLogicModelMetaProvider.SingletonHolder.class, 100);
    assertPriority(DefaultLogicFlowProvider.SingletonHolder.class, 10);
  }

  private <T> void assertServiceDiscoverable(Class<T> serviceType, Class<? extends T> expectedProvider) {
    List<String> discoveredProviders = new ArrayList<String>();
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

  private void assertPriority(Class<?> providerClass, int expectedPriority) {
    Priority priority = providerClass.getAnnotation(Priority.class);
    assertTrue(priority != null, () -> "Missing @Priority on " + providerClass.getName());
    assertEquals(expectedPriority, priority.value());
  }

  private List<String> readDescriptorLines(String resourcePath) throws Exception {
    Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resourcePath);
    List<String> lines = new ArrayList<String>();

    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();
      try (InputStream inputStream = resource.openStream();
           BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
    }

    assertTrue(!lines.isEmpty(), () -> "Missing SPI descriptor: " + resourcePath);
    return lines;
  }

  private List<String> readProviderEntries(List<String> descriptorLines) {
    List<String> providers = new ArrayList<String>();
    for (String line : descriptorLines) {
      String provider = line.trim();
      if (!provider.isEmpty() && !provider.startsWith("#")) {
        providers.add(provider);
      }
    }
    return providers;
  }
}
