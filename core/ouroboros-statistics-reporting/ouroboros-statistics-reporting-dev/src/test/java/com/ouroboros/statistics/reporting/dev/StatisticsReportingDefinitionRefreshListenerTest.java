package com.ouroboros.statistics.reporting.dev;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.event.UpdateModelEvent;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StatisticsReportingDefinitionRefreshListenerTest {

  @Test
  void shouldRefreshOnlyTheCenterThatOwnsTheChangedModel() {
    DefaultDatasetDefinitionCenter datasetCenter = mock(DefaultDatasetDefinitionCenter.class);
    DefaultChartDefinitionCenter viewCenter = mock(DefaultChartDefinitionCenter.class);
    StatisticsReportingDefinitionRefreshListener listener = new StatisticsReportingDefinitionRefreshListener(
        datasetCenter, viewCenter);

    listener.onModelChanged(new UpdateModelEvent(this, "test-app", new LowcodeDatasetDefinition()));
    verify(datasetCenter).refresh();
    verify(viewCenter, never()).refresh();
    listener.onModelChanged(new UpdateModelEvent(this, "test-app", new LowcodeChartDefinition()));
    verify(viewCenter).refresh();
  }
}
