package com.ouroboros.statistics.reporting.dev.controller;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.service.impl.ChartDevService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;

@RestController
@RequestMapping("/api/dev/{appName}/chart")
public class ChartOptionsController {
  private final ChartDevService chartDevService;

  public ChartOptionsController(ChartDevService chartDevService) {
    this.chartDevService = Objects.requireNonNull(chartDevService, "chartDevService is required");
  }

  @GetMapping("/options")
  public ItemsWebApiResult<SelectOption> options(@PathVariable("appName") String appName) {
    List<SelectOption> options = chartDevService.search(Collections.emptyMap())
        .stream()
        .filter(LowcodeChartDefinition.class::isInstance)
        .map(LowcodeChartDefinition.class::cast)
        .filter(chart -> Boolean.TRUE.equals(chart.getIsEnabled()))
        .map(this::toOption)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(options);
  }

  private SelectOption toOption(LowcodeChartDefinition chart) {
    String label = isBlank(chart.getTitle()) ? chart.getName() : chart.getTitle() + " (" + chart.getName() + ")";
    SelectOption option = new SelectOption(chart.getName(), label);
    option.addExtraProp("datasetName", chart.getDatasetName());
    option.addExtraProp("chartType", chart.getChartType());
    option.addExtraProp("isEnabled", chart.getIsEnabled());
    return option;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
