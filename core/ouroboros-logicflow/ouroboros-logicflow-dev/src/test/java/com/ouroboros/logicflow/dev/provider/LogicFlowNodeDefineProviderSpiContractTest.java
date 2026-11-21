package com.ouroboros.logicflow.dev.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.Test;

import com.ouroboros.logicflow.authoring.model.NodeDefine;
import com.ouroboros.logicflow.authoring.provider.JarLogicFlowNodeDefineProvider;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.logicflow.dev.controller.LogicflowNodeDefineController;

public class LogicFlowNodeDefineProviderSpiContractTest {

  @Test
  public void shouldDiscoverNodeDefineProviderFromSpi() {
    List<String> discoveredProviders = new ArrayList<String>();
    for (LogicFlowNodeDefineProvider provider : ServiceLoader.load(LogicFlowNodeDefineProvider.class)) {
      discoveredProviders.add(provider.getClass().getName());
      if (provider instanceof JarLogicFlowNodeDefineProvider) {
        return;
      }
    }
    fail("Provider " + JarLogicFlowNodeDefineProvider.class.getName()
        + " not found. Discovered: " + discoveredProviders);
  }

  @Test
  public void shouldUseNodeDefineProviderDescriptorName() throws Exception {
    List<String> providers = readProviderEntries(readDescriptorLines(
        "META-INF/services/com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider"));

    assertTrue(providers.contains("com.ouroboros.logicflow.authoring.provider.JarLogicFlowNodeDefineProvider"));
  }

  @Test
  public void shouldReadClasspathNodeDefineResourcesThroughProviderAndController() {
    Enumeration<URL> returnNodeResources = getResources("META-INF/ouroboros/metadata/ReturnNode.flow-node.json");
    assertTrue(returnNodeResources.hasMoreElements());

    List<NodeDefine> providerNodeDefines = new JarLogicFlowNodeDefineProvider().getNodeDefines();
    assertFalse(providerNodeDefines.isEmpty());
    assertTrue(providerNodeDefines.stream().anyMatch(node -> "return".equals(node.getType())));

    List<NodeDefine> controllerNodeDefines =
        new LogicflowNodeDefineController().getNodeDefines().getData();
    assertNotNull(controllerNodeDefines);
    assertFalse(controllerNodeDefines.isEmpty());
    assertTrue(controllerNodeDefines.stream().anyMatch(node -> "return".equals(node.getType())));
    assertEquals(providerNodeDefines.size(), controllerNodeDefines.size());
  }

  @Test
  public void shouldExposeSwitchSelectiveOutputMetadataFromFormalNodeDefineResource() {
    NodeDefine switchNode = new JarLogicFlowNodeDefineProvider().getNodeDefines()
        .stream()
        .filter(node -> "switch".equals(node.getType()))
        .findFirst()
        .orElse(null);

    assertNotNull(switchNode);

    List<Map<String, Object>> scaffoldOutputPorts =
        (List<Map<String, Object>>) switchNode.getScaffold().get("outputPorts");
    assertFalse(scaffoldOutputPorts.isEmpty());
    assertEquals("switch-branches", scaffoldOutputPorts.get(0).get("exclusiveGroupId"));
    assertTrue(Boolean.TRUE.equals(scaffoldOutputPorts.get(0).get("defaultBranch")));

    String autoFunction = (String) switchNode.getPortRules().get("output").get("autoFunction");
    assertNotNull(autoFunction);
    assertTrue(autoFunction.contains("exclusiveGroupId = 'switch-branches'"));
    assertTrue(autoFunction.contains("defaultBranch: false"));
    assertTrue(autoFunction.contains("defaultBranch: true"));
  }

  private Enumeration<URL> getResources(String resourcePath) {
    try {
      return Thread.currentThread().getContextClassLoader().getResources(resourcePath);
    } catch (Exception e) {
      throw new AssertionError("Failed to load resource: " + resourcePath, e);
    }
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

    assertTrue("Missing SPI descriptor: " + resourcePath, !lines.isEmpty());
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
