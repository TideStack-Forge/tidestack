package com.ouroboros.configuration.provider;

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

import com.ouroboros.configuration.metadata.ConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConfigurationMetadataProviderSpiContractTest {

  @Test
  void shouldDiscoverMetadataProvidersFromSpi() {
    assertServiceDiscoverable(ConfigurationMetadataProvider.class, FileConfigurationMetadataProvider.Proxy.class);
    assertServiceDiscoverable(ConfigurationMetadataProvider.class, JarConfigurationMetadataProvider.class);
    assertServiceDiscoverable(ConfigurationGroupMetadataProvider.class, FileConfigurationGroupMetadataProvider.Proxy.class);
    assertServiceDiscoverable(ConfigurationGroupMetadataProvider.class, JarConfigurationGroupMetadataProvider.class);
  }

  @Test
  void shouldUseMetadataDescriptorNames() throws Exception {
    String metadataResourcePath = "META-INF/services/com.ouroboros.configuration.metadata.ConfigurationMetadataProvider";
    String groupResourcePath = "META-INF/services/com.ouroboros.configuration.metadata.ConfigurationGroupMetadataProvider";

    List<String> metadataProviders = readProviderEntries(readDescriptorLines(metadataResourcePath));
    List<String> groupProviders = readProviderEntries(readDescriptorLines(groupResourcePath));

    assertTrue(metadataProviders.contains("com.ouroboros.configuration.provider.FileConfigurationMetadataProvider$Proxy"));
    assertTrue(metadataProviders.contains("com.ouroboros.configuration.provider.JarConfigurationMetadataProvider"));
    assertTrue(groupProviders.contains("com.ouroboros.configuration.provider.FileConfigurationGroupMetadataProvider$Proxy"));
    assertTrue(groupProviders.contains("com.ouroboros.configuration.provider.JarConfigurationGroupMetadataProvider"));
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
