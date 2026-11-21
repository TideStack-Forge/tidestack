package com.ouroboros.statistics.reporting.runtime.center;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.ChartDefinitionProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChartDefinitionCenterTest {

  @Test
  void shouldAllowHigherOrderChartsToOverrideLowerOrderCharts() {
    DefaultChartDefinitionCenter center = new DefaultChartDefinitionCenter(Arrays.asList(
        provider(100, chart("demo.report", "Low", true)),
        provider(200, chart("demo.report", "High", true))
    ));

    assertEquals("High", center.getDefinition("demo.report").get().getTitle());
  }

  @Test
  void shouldAllowHigherOrderDisabledChartToMaskLowerOrderChart() {
    DefaultChartDefinitionCenter center = new DefaultChartDefinitionCenter(Arrays.asList(
        provider(100, chart("demo.report", "Low", true)),
        provider(200, chart("demo.report", "Disabled override", false))
    ));

    assertTrue(center.getDefinition("demo.report").isEmpty());
  }

  @Test
  void shouldRejectDuplicateChartsInSameOrder() {
    PlatformException error = assertThrows(PlatformException.class,
        () -> new DefaultChartDefinitionCenter(Arrays.asList(
            provider(100, chart("demo.report", "One", true)),
            provider(100, chart("demo.report", "Two", true))
        )));

    assertTrue(error.getMessage().contains("Duplicate chart definition in the same precedence tier: demo.report"));
  }

  @Test
  void shouldExcludeDisabledCharts() {
    DefaultChartDefinitionCenter center = new DefaultChartDefinitionCenter(Collections.singletonList(
        provider(100,
            chart("demo.report", "Enabled", true),
            chart("demo.disabled", "Disabled", false))
    ));

    assertEquals(Collections.singletonList("demo.report"),
        center.getDefinitions().stream().map(ChartDefinition::getName)
            .collect(java.util.stream.Collectors.toList()));
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "demo/*", "demo/{name}", "demo%2Fname", ".."})
  void shouldRejectUnsafeNamesBeforeTheyCanBeProjected(String name) {
    PlatformException error = assertThrows(PlatformException.class,
        () -> new DefaultChartDefinitionCenter(Collections.singletonList(provider(100, chart(name, "Unsafe", true)))));

    assertTrue(error.getMessage().contains("chart name"));
  }

  @Test
  void shouldKeepPreviousSnapshotIfRefreshFailsValidation() {
    AtomicReference<java.util.List<ChartDefinition>> definitions = new AtomicReference<>(
        Collections.singletonList(chart("demo.report", "Before", true)));
    DefaultChartDefinitionCenter center = new DefaultChartDefinitionCenter(Collections.singletonList(definitions::get));
    definitions.set(Collections.singletonList(chart("demo/*", "Invalid", true)));

    assertThrows(PlatformException.class, center::refresh);

    assertEquals("Before", center.getDefinition("demo.report").orElseThrow().getTitle());
  }

  @Test
  void shouldReloadChartsWhenMetadataRefreshes() {
    AtomicReference<java.util.List<ChartDefinition>> definitions = new AtomicReference<>(
        Collections.singletonList(chart("demo.report", "Before", true)));
    DefaultChartDefinitionCenter center = new DefaultChartDefinitionCenter(Collections.singletonList(
        () -> definitions.get()));

    definitions.set(Collections.singletonList(chart("demo.report", "After", true)));
    center.onMetadataRefreshing();

    assertEquals("After", center.getDefinition("demo.report").get().getTitle());
  }

  private static ChartDefinitionProvider provider(int order, ChartDefinition... definitions) {
    return new ChartDefinitionProvider() {
      @Override
      public java.util.List<ChartDefinition> getDefinitions() {
        return Arrays.asList(definitions);
      }

      @Override
      public int getOrder() {
        return order;
      }
    };
  }

  private static ChartDefinition chart(String name, String title, boolean isEnabled) {
    ChartDefinition chart = new ChartDefinition();
    chart.setName(name);
    chart.setTitle(title);
    chart.setIsEnabled(Boolean.valueOf(isEnabled));
    chart.setDatasetName("demo.sales");
    chart.setChartType("table");
    chart.setParams(Collections.<String, Object>singletonMap("columns", Collections.singletonList("id")));
    return chart;
  }
}
