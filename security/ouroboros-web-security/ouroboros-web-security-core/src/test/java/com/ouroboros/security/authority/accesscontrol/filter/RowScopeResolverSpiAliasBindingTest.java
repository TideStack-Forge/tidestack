package com.ouroboros.security.authority.accesscontrol.filter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowScopeResolverSpiAliasBindingTest {

  private static final String ROW_SCOPE_RESOLVER_CLASS_NAME =
      "com.ouroboros.security.authority.accesscontrol.filter.RowScopeResolver";
  private static final String LEGACY_DESCRIPTOR =
      "META-INF/services/com.ouroboros.security.authority.accesscontrol.filter.AccessControlFilterBuilder";
  private static final String ROW_SCOPE_DESCRIPTOR =
      "META-INF/services/com.ouroboros.security.authority.accesscontrol.filter.RowScopeResolver";
  private static final String CORE_MODULE_NAME = "ouroboros-web-security-core";
  private static final String ORGANIZATION_MODULE_NAME = "ouroboros-organization-structure-runtime";
  private static final String DEFAULT_PROVIDER =
      "com.ouroboros.security.authority.accesscontrol.filter.DefaultACFilterBuilder";
  private static final String ORGANIZATION_PROVIDER =
      "com.ouroboros.organizationstructure.security.OrganizationStructureAccessControlFilterBuilder$SingletonHolder";
  private static final String CORE_LEGACY_DESCRIPTOR_SOURCE =
      "security/ouroboros-web-security/ouroboros-web-security-core/src/main/resources/"
          + "META-INF/services/com.ouroboros.security.authority.accesscontrol.filter.AccessControlFilterBuilder";
  private static final String CORE_ROW_SCOPE_DESCRIPTOR_SOURCE =
      "security/ouroboros-web-security/ouroboros-web-security-core/src/main/resources/"
          + "META-INF/services/com.ouroboros.security.authority.accesscontrol.filter.RowScopeResolver";
  private static final String ORGANIZATION_LEGACY_DESCRIPTOR_SOURCE =
      "ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/resources/"
          + "META-INF/services/com.ouroboros.security.authority.accesscontrol.filter.AccessControlFilterBuilder";
  private static final String ORGANIZATION_ROW_SCOPE_DESCRIPTOR_SOURCE =
      "ability/ouroboros-organization-structure/ouroboros-organization-structure-runtime/src/main/resources/"
          + "META-INF/services/com.ouroboros.security.authority.accesscontrol.filter.RowScopeResolver";

  @Test
  void shouldBindLegacyAndRowScopeResolverSpisToTheSameProviders() throws Exception {
    Class<?> rowScopeResolverType = assertDoesNotThrow(
        () -> Class.forName(ROW_SCOPE_RESOLVER_CLASS_NAME),
        "RowScopeResolver SPI type should exist in the canonical access-control filter package");

    Method buildMethod = assertDoesNotThrow(
        () -> rowScopeResolverType.getMethod("build", java.util.Map.class, AccessControlDefine.class),
        "RowScopeResolver should expose the same build(Map<String, Object>, AccessControlDefine) contract");
    assertEquals(java.util.Map.class, buildMethod.getReturnType());

    List<URL> legacyResources = readDescriptorResources(LEGACY_DESCRIPTOR);
    assertModuleDescriptorPresent(legacyResources, LEGACY_DESCRIPTOR, CORE_MODULE_NAME);

    List<URL> rowScopeResources = readDescriptorResources(ROW_SCOPE_DESCRIPTOR);
    assertModuleDescriptorPresent(rowScopeResources, ROW_SCOPE_DESCRIPTOR, CORE_MODULE_NAME);

    Path repoRoot = findRepoRoot();
    assertDescriptorSourceContains(repoRoot.resolve(CORE_LEGACY_DESCRIPTOR_SOURCE), DEFAULT_PROVIDER);
    assertDescriptorSourceContains(repoRoot.resolve(CORE_ROW_SCOPE_DESCRIPTOR_SOURCE), DEFAULT_PROVIDER);
    assertDescriptorSourceContains(repoRoot.resolve(ORGANIZATION_LEGACY_DESCRIPTOR_SOURCE), ORGANIZATION_PROVIDER);
    assertDescriptorSourceContains(repoRoot.resolve(ORGANIZATION_ROW_SCOPE_DESCRIPTOR_SOURCE), ORGANIZATION_PROVIDER);

    Set<String> legacyDescriptorProviders = readProviderEntries(legacyResources);
    assertTrue(legacyDescriptorProviders.contains(DEFAULT_PROVIDER),
        () -> "Legacy SPI descriptor should still list the core provider. Found: " + legacyDescriptorProviders);

    Set<String> rowScopeDescriptorProviders = readProviderEntries(rowScopeResources);
    assertTrue(rowScopeDescriptorProviders.contains(DEFAULT_PROVIDER),
        () -> "RowScopeResolver SPI descriptor should list the core provider. Found: " + rowScopeDescriptorProviders);
    assertEquals(legacyDescriptorProviders, rowScopeDescriptorProviders,
        "Legacy and RowScopeResolver descriptors should expose the same core-module provider set");

    Set<String> legacyLoadedProviders = loadProviderClassNames(AccessControlFilterBuilder.class);
    assertTrue(legacyLoadedProviders.contains(DEFAULT_PROVIDER),
        () -> "Legacy AccessControlFilterBuilder SPI should discover the core provider. Found: " + legacyLoadedProviders);

    Set<String> rowScopeLoadedProviders = loadProviderClassNames(rowScopeResolverType);
    assertTrue(rowScopeLoadedProviders.contains(DEFAULT_PROVIDER),
        () -> "RowScopeResolver SPI should discover the core provider. Found: " + rowScopeLoadedProviders);
    assertEquals(legacyLoadedProviders, rowScopeLoadedProviders,
        "Legacy and RowScopeResolver SPI entrypoints should load the same core-module provider set");
  }

  private List<URL> readDescriptorResources(String resourcePath) throws Exception {
    Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resourcePath);
    List<URL> urls = new ArrayList<>();
    while (resources.hasMoreElements()) {
      urls.add(resources.nextElement());
    }
    assertTrue(!urls.isEmpty(), () -> "Missing SPI descriptor: " + resourcePath);
    return urls;
  }

  private void assertModuleDescriptorPresent(List<URL> resources, String resourcePath, String moduleName) {
    for (URL resource : resources) {
      if (resource.toString().contains(moduleName)) {
        return;
      }
    }
    throw new AssertionError("Missing descriptor " + resourcePath + " from module " + moduleName + ". Found: " + resources);
  }

  private Path findRepoRoot() {
    Path current = Paths.get("").toAbsolutePath();
    while (current != null) {
      if (Files.exists(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("security"))
          && Files.isDirectory(current.resolve("ability"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new AssertionError("Could not locate repository root from " + Paths.get("").toAbsolutePath());
  }

  private void assertDescriptorSourceContains(Path descriptorPath, String providerName) throws Exception {
    assertTrue(Files.exists(descriptorPath), () -> "Missing descriptor source: " + descriptorPath);

    try (BufferedReader reader = Files.newBufferedReader(descriptorPath, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String provider = line.trim();
        if (providerName.equals(provider)) {
          return;
        }
      }
    }

    throw new AssertionError("Descriptor " + descriptorPath + " does not contain provider " + providerName);
  }

  private Set<String> readProviderEntries(List<URL> resources) throws Exception {
    Set<String> providers = new LinkedHashSet<>();
    for (URL resource : resources) {
      try (InputStream inputStream = resource.openStream();
           BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String provider = line.trim();
          if (!provider.isEmpty() && !provider.startsWith("#")) {
            providers.add(provider);
          }
        }
      }
    }
    return providers;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private Set<String> loadProviderClassNames(Class<?> serviceType) {
    Set<String> providerClassNames = new LinkedHashSet<>();
    for (Object provider : ServiceLoader.load((Class) serviceType)) {
      providerClassNames.add(provider.getClass().getName());
    }
    return providerClassNames;
  }
}
