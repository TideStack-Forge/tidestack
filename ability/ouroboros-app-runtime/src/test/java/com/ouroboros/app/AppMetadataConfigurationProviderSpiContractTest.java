package com.ouroboros.app;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AppMetadataConfigurationProviderSpiContractTest {

  @Test
  void shouldDiscoverAppMetadataProviderProxyFromSpi() {
    assertServiceDiscoverable(ConfigurationMetadataProvider.class, AppMetadataConfigurationProvider.Proxy.class);
  }

  @Test
  void shouldUseMetadataDescriptorNameForAppMetadataProvider() throws Exception {
    String resourcePath = "META-INF/services/com.ouroboros.configuration.metadata.ConfigurationMetadataProvider";
    String expectedProvider = "com.ouroboros.app.AppMetadataConfigurationProvider$Proxy";

    List<String> descriptorLines = readDescriptorLines(resourcePath);

    assertTrue(readProviderEntries(descriptorLines).contains(expectedProvider));
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
