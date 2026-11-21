package com.ouroboros.statistics.reporting.dev.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.service.impl.ChartDevService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChartOptionsControllerTest {
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DatasetDevService datasetDevService = null;
    ChartDevService chartDevService = new ChartDevService(datasetDevService) {
      @Override
      public List<?> search(Map<String, Object> searchParam) {
        return Arrays.asList(
            chart("demo.salesReport", "Sales Report", "demo.sales", "bar", true),
            chart("demo.metric", "Metric", "demo.sales", "metric-card", false)
        );
      }
    };
    mockMvc = MockMvcBuilders.standaloneSetup(new ChartOptionsController(chartDevService)).build();
  }

  @Test
  void optionsEndpoint_shouldReturnChartSelectOptions() throws Exception {
    mockMvc.perform(get("/api/dev/demo-app/chart/options"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[0].value").value("demo.salesReport"))
        .andExpect(jsonPath("$.data.items[0].label").value("Sales Report (demo.salesReport)"))
        .andExpect(jsonPath("$.data.items[0].datasetName").value("demo.sales"))
        .andExpect(jsonPath("$.data.items[0].chartType").value("bar"))
        .andExpect(jsonPath("$.data.items[0].isEnabled").value(true))
        .andExpect(jsonPath("$.data.items.length()").value(1));
  }

  private LowcodeChartDefinition chart(String name,
                                                 String title,
                                                 String datasetName,
                                                 String chartType,
                                                 boolean isEnabled) {
    LowcodeChartDefinition chart = new LowcodeChartDefinition();
    chart.setName(name);
    chart.setTitle(title);
    chart.setDatasetName(datasetName);
    chart.setChartType(chartType);
    chart.setIsEnabled(Boolean.valueOf(isEnabled));
    return chart;
  }
}
