package com.ouroboros.statistics.reporting.dev;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.event.DevAppModelEvent;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;

@Component
public class StatisticsReportingDefinitionRefreshListener {
  private final DefaultDatasetDefinitionCenter datasetDefinitionCenter;
  private final DefaultChartDefinitionCenter chartDefinitionCenter;

  public StatisticsReportingDefinitionRefreshListener(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                                      DefaultChartDefinitionCenter chartDefinitionCenter) {
    this.datasetDefinitionCenter = datasetDefinitionCenter;
    this.chartDefinitionCenter = chartDefinitionCenter;
  }

  @EventListener
  public void onModelChanged(DevAppModelEvent event) {
    Object model = event.getModel();
    if (model instanceof DatasetDefinition) {
      datasetDefinitionCenter.refresh();
    } else if (model instanceof ChartDefinition) {
      chartDefinitionCenter.refresh();
    }
  }
}
