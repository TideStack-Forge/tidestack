package com.ouroboros.businessmodel.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessModelFeaturePluginRegistryTest {

  @Test
  void preparedPlanFreezesActionDeclarationsAndDefersDataProjection() {
    var action = new com.ouroboros.businessmodel.behavior.ActionDefinition(); action.setName("queryHistory");
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var plan = new BusinessModelFeaturePlan("history", List.of(action), context -> calls.incrementAndGet());
    action.setName("changed");
    assertEquals("history", plan.getFeatureId());
    assertEquals("queryHistory", plan.getActions().getFirst().getName());
    plan.getActions().getFirst().setName("alsoChanged");
    assertEquals("queryHistory", plan.getActions().getFirst().getName());
    assertEquals(0, calls.get());
    plan.project(new com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext<>(
        unresolvedModel()));
    assertEquals(1, calls.get());
  }

  @Test
  void existingPluginsCanPrepareWithoutAnEditorOrProjector() {
    var plan = plugin("editorOnly", BusinessModelTypes.DEFAULT).prepare(
        new com.ouroboros.businessmodel.meta.BusinessModelMeta(), unresolvedModel());
    assertEquals(List.of(), plan.getActions());
    plan.project(new com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext<>(
        unresolvedModel()));
  }

  @Test
  void legacyProjectorRunsOnceInItsOriginalContextAfterPreparation() {
    var meta = new com.ouroboros.businessmodel.meta.BusinessModelMeta();
    var context = new com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext<>(unresolvedModel());
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var plugin = pluginWithProjector("legacy", List.of(BusinessModelTypes.DEFAULT), (source, projection) -> {
      org.junit.jupiter.api.Assertions.assertSame(meta, source);
      org.junit.jupiter.api.Assertions.assertSame(context, projection);
      calls.incrementAndGet();
    });
    var plan = plugin.prepare(meta, context.getSourceModel());
    assertEquals(0, calls.get());
    plan.project(context);
    assertEquals(1, calls.get());
  }

  private static com.ouroboros.businessmodel.BusinessModel unresolvedModel() {
    return com.ouroboros.businessmodel.BusinessModel.class.cast(java.lang.reflect.Proxy.newProxyInstance(
        BusinessModelFeaturePluginRegistryTest.class.getClassLoader(),
        new Class<?>[]{com.ouroboros.businessmodel.BusinessModel.class}, (proxy, method, args) -> {
          throw new AssertionError("Feature preparation must not invoke model runtime: " + method.getName());
        }));
  }

  @Test
  void getPluginsForModelType_filtersDefaultSpecificAndAllPlugins() {
    BusinessModelFeaturePlugin defaultPlugin = plugin("default-plugin", BusinessModelTypes.DEFAULT);
    BusinessModelFeaturePlugin compositeViewPlugin = plugin("composite-view-plugin", BusinessModelTypes.COMPOSITE_VIEW);
    BusinessModelFeaturePlugin allPlugin = plugin("all-plugin", BusinessModelTypes.ALL);
    BusinessModelFeaturePluginRegistry registry = new BusinessModelFeaturePluginRegistry(Arrays.asList(
        defaultPlugin,
        compositeViewPlugin,
        allPlugin
    ));

    assertEquals(Arrays.asList(defaultPlugin, allPlugin), registry.getPluginsForModelType(BusinessModelTypes.DEFAULT));
    assertEquals(Arrays.asList(compositeViewPlugin, allPlugin), registry.getPluginsForModelType(BusinessModelTypes.COMPOSITE_VIEW));
    assertEquals(Arrays.asList(defaultPlugin, allPlugin), registry.getPluginsForModelType(null));
  }

  @Test
  void constructor_rejectsPluginsWithoutSupportedModelTypes() {
    BusinessModelFeaturePlugin plugin = plugin("blank-plugin", " ");

    assertThrows(IllegalStateException.class, () -> new BusinessModelFeaturePluginRegistry(Collections.singletonList(plugin)));
  }

  @Test
  void constructor_rejectsPluginsWithNullSupportedModelTypes() {
    BusinessModelFeaturePlugin plugin = pluginWithModelTypes("null-plugin", null);

    assertThrows(IllegalStateException.class, () -> new BusinessModelFeaturePluginRegistry(Collections.singletonList(plugin)));
  }

  @Test
  void constructor_rejectsPluginsWithEmptySupportedModelTypes() {
    BusinessModelFeaturePlugin plugin = pluginWithModelTypes("empty-plugin", Collections.emptyList());

    assertThrows(IllegalStateException.class, () -> new BusinessModelFeaturePluginRegistry(Collections.singletonList(plugin)));
  }

  @Test
  void constructor_rejectsPluginsWithOnlyBlankSupportedModelTypes() {
    BusinessModelFeaturePlugin plugin = pluginWithModelTypes("blank-list-plugin", Arrays.asList(" ", ""));

    assertThrows(IllegalStateException.class, () -> new BusinessModelFeaturePluginRegistry(Collections.singletonList(plugin)));
  }

  private BusinessModelFeaturePlugin plugin(String pluginId, String supportedModelType) {
    return pluginWithModelTypes(pluginId, Collections.singletonList(supportedModelType));
  }

  private BusinessModelFeaturePlugin pluginWithModelTypes(String pluginId, List<String> supportedModelTypes) {
    return pluginWithProjector(pluginId, supportedModelTypes, null);
  }

  private BusinessModelFeaturePlugin pluginWithProjector(String pluginId, List<String> supportedModelTypes, PluginProjector projector) {
    return new BusinessModelFeaturePlugin() {
      @Override
      public String getPluginId() {
        return pluginId;
      }

      @Override
      public String getDisplayName() {
        return pluginId;
      }

      @Override
      public PluginEditorSchemaContributor getEditorContributor() {
        return null;
      }

      @Override
      public PluginProjector getProjector() {
        return projector;
      }

      @Override
      public List<String> getSupportedModelTypes() {
        return supportedModelTypes;
      }

      @Override
      public PluginMetadata getMetadata() {
        return null;
      }
    };
  }
}
