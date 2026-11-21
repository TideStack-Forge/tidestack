package com.ouroboros.statistics.reporting.runtime.authority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.DefaultAuthority;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;

/** Projects independent execute and export permissions from effective definitions. */
public class StatisticsReportingDefinitionAuthorityProvider implements AuthorityProvider {
  private final DefaultChartDefinitionCenter charts;
  private final DefaultDatasetDefinitionCenter datasets;

  public StatisticsReportingDefinitionAuthorityProvider(DefaultChartDefinitionCenter charts,
                                                       DefaultDatasetDefinitionCenter datasets) {
    this.charts = Objects.requireNonNull(charts, "chartDefinitionCenter is required");
    this.datasets = Objects.requireNonNull(datasets, "datasetDefinitionCenter is required");
  }

  @Override
  public List<Authority> getAuthority(String fullName) {
    if (fullName == null) {
      return Collections.emptyList();
    }
    return getAllAuthorities().stream()
        .filter(authority -> fullName.equalsIgnoreCase(authority.getFullName()))
        .toList();
  }

  @Override
  public List<Authority> getAllAuthorities() {
    List<Authority> authorities = new ArrayList<>();
    charts.getDefinitions().forEach(chart -> project(authorities, "chart", chart.getName(), chart.getTitle(),
        "/api/runtime/statistics-reporting/charts/"));
    datasets.getDefinitions().forEach(dataset -> project(authorities, "dataset", dataset.getName(), dataset.getTitle(),
        "/api/statistics-reporting/datasets/"));
    return List.copyOf(authorities);
  }

  private void project(List<Authority> authorities, String type, String name, String title, String apiPrefix) {
    for (String operation : List.of("execute", "export")) {
      DefaultAuthority authority = new DefaultAuthority();
      authority.setFullName("statisticsReporting.runtime." + type + "."
          + name.toLowerCase(Locale.ROOT) + "." + operation);
      String label = title == null || title.isBlank() ? name : title;
      authority.setTitle(label + ("execute".equals(operation) ? " · 查询" : " · 导出"));
      authority.setApis(List.of("POST " + apiPrefix + "{" + type + "Name:(?i)" + Pattern.quote(name) + "}/" + operation));
      authorities.add(authority);
    }
  }
}
