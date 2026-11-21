package com.ouroboros.statistics.reporting.runtime.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.provider.ChartDefinitionProvider;

class StatisticsReportingDefinitionAuthorityProviderTest {

  @Test
  void shouldProjectPerChartAuthoritiesOntoTheFixedRuntimeRoutes() {
    ChartDefinition chart = chart("demo.sales", "销售趋势");
    StatisticsReportingDefinitionAuthorityProvider provider = new StatisticsReportingDefinitionAuthorityProvider(
        new DefaultChartDefinitionCenter(Collections.<ChartDefinitionProvider>singletonList(
            () -> Collections.singletonList(chart))), new com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter(List.of()));

    List<Authority> authorities = provider.getAuthority("statisticsReporting.runtime.chart.demo.sales.execute");

    assertEquals(1, authorities.size());
    assertEquals("销售趋势 · 查询", authorities.get(0).getTitle());
    assertEquals(List.of(
        "POST /api/runtime/statistics-reporting/charts/{chartName:(?i)\\Qdemo.sales\\E}/execute"), authorities.get(0).getApis());
  }

  @Test
  void shouldRefreshProjectedAuthoritiesWithTheDefinitionCenter() {
    ChartDefinition chart = chart("demo.sales", "Before");
    java.util.concurrent.atomic.AtomicReference<List<ChartDefinition>> definitions =
        new java.util.concurrent.atomic.AtomicReference<>(Collections.singletonList(chart));
    DefaultChartDefinitionCenter center = new DefaultChartDefinitionCenter(Collections.<ChartDefinitionProvider>singletonList(
        definitions::get));
    StatisticsReportingDefinitionAuthorityProvider provider = new StatisticsReportingDefinitionAuthorityProvider(center, new com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter(List.of()));

    definitions.set(Collections.singletonList(chart("demo.sales", "After")));
    center.refresh();

    assertTrue(provider.getAuthority("statisticsReporting.runtime.chart.demo.sales.execute").stream()
        .anyMatch(authority -> "After · 查询".equals(authority.getTitle())));
  }

  @Test
  void shouldMatchCaseInsensitiveNamesAndSeparateOperationsAndDefinitions() {
    var dataset = new com.ouroboros.statistics.reporting.definition.DatasetDefinition();
    dataset.setName("demo.Sales");
    dataset.setIsEnabled(true);
    var provider = new StatisticsReportingDefinitionAuthorityProvider(
        new DefaultChartDefinitionCenter(List.of(() -> List.of(chart("demo.Sales", "Sales")))),
        new com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter(List.of(() -> List.of(dataset))));
    assertEquals(4, provider.getAllAuthorities().size());
    var parser = new org.springframework.web.util.pattern.PathPatternParser();
    for (String type : List.of("chart", "dataset")) {
      String prefix = "chart".equals(type) ? "/api/runtime/statistics-reporting/charts/" : "/api/statistics-reporting/datasets/";
      for (String operation : List.of("execute", "export")) {
        var authority = provider.getAuthority("STATISTICSREPORTING.RUNTIME." + type + ".DEMO.SALES." + operation).getFirst();
        var pattern = parser.parse(authority.getApis().getFirst().substring(5));
        assertTrue(pattern.matches(org.springframework.http.server.PathContainer.parsePath(prefix + "DEMO.sales/" + operation)));
        org.junit.jupiter.api.Assertions.assertFalse(pattern.matches(
            org.springframework.http.server.PathContainer.parsePath(prefix + "demoXsales/" + operation)));
        org.junit.jupiter.api.Assertions.assertFalse(pattern.matches(
            org.springframework.http.server.PathContainer.parsePath(prefix + "demo.other/" + operation)));
        org.junit.jupiter.api.Assertions.assertFalse(pattern.matches(
            org.springframework.http.server.PathContainer.parsePath(prefix + "demo.sales/" + ("execute".equals(operation) ? "export" : "execute"))));
      }
    }
  }

  private ChartDefinition chart(String name, String title) {
    ChartDefinition chart = new ChartDefinition();
    chart.setName(name);
    chart.setTitle(title);
    chart.setIsEnabled(Boolean.TRUE);
    return chart;
  }
}
