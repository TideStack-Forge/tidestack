package com.ouroboros.statistics.reporting.runtime.center;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDatasetDefinitionCenterTest {

  @Test
  void shouldAllowHigherOrderDefinitionsToOverrideLowerOrderDefinitions() {
    DefaultDatasetDefinitionCenter center = new DefaultDatasetDefinitionCenter(Arrays.asList(
        provider(100, dataset("demo.sales", "Low", true)),
        provider(200, dataset("demo.SALES", "High", true))
    ));

    assertEquals("High", center.getDefinition("DEMO.Sales").get().getTitle());
    assertEquals(1, center.getDefinitions().size());
  }

  @Test
  void shouldAllowHigherOrderDisabledDefinitionToMaskLowerOrderDefinition() {
    DefaultDatasetDefinitionCenter center = new DefaultDatasetDefinitionCenter(Arrays.asList(
        provider(100, dataset("demo.sales", "Low", true)),
        provider(200, dataset("demo.sales", "Disabled override", false))
    ));

    assertTrue(center.getDefinition("demo.sales").isEmpty());
  }

  @Test
  void shouldRejectDuplicateDefinitionsInSameOrder() {
    PlatformException error = assertThrows(PlatformException.class,
        () -> new DefaultDatasetDefinitionCenter(Arrays.asList(
            provider(100, dataset("demo.sales", "One", true)),
            provider(100, dataset("demo.SALES", "Two", true))
        )));

    assertTrue(error.getMessage().contains("Duplicate dataset definition in the same precedence tier: demo.SALES"));
  }

  @Test
  void shouldIgnoreBlankAndDisabledDefinitions() {
    DefaultDatasetDefinitionCenter center = new DefaultDatasetDefinitionCenter(Collections.singletonList(
        provider(100,
            dataset("demo.sales", "Enabled", true),
            dataset("demo.disabled", "Disabled", false),
            dataset(" ", "Blank", true))
    ));

    assertEquals(Collections.singletonList("demo.sales"),
        center.getDefinitions().stream().map(DatasetDefinition::getName).collect(java.util.stream.Collectors.toList()));
  }

  @Test
  void shouldReloadDefinitionsWhenMetadataRefreshes() {
    AtomicReference<java.util.List<DatasetDefinition>> definitions = new AtomicReference<>(
        Collections.singletonList(dataset("demo.sales", "Before", true)));
    DefaultDatasetDefinitionCenter center = new DefaultDatasetDefinitionCenter(Collections.singletonList(
        () -> definitions.get()));

    definitions.set(Collections.singletonList(dataset("demo.sales", "After", true)));
    center.onMetadataRefreshing();

    assertEquals("After", center.getDefinition("demo.sales").get().getTitle());
  }

  private static DatasetDefinitionProvider provider(int order, DatasetDefinition... definitions) {
    return new DatasetDefinitionProvider() {
      @Override
      public java.util.List<DatasetDefinition> getDefinitions() {
        return Arrays.asList(definitions);
      }

      @Override
      public int getOrder() {
        return order;
      }
    };
  }

  private static DatasetDefinition dataset(String name, String title, boolean isEnabled) {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName(name);
    dataset.setTitle(title);
    dataset.setIsEnabled(Boolean.valueOf(isEnabled));
    return dataset;
  }
}
