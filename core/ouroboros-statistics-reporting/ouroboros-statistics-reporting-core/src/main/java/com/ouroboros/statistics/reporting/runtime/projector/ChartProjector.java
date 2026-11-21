package com.ouroboros.statistics.reporting.runtime.projector;

import com.ouroboros.statistics.reporting.runtime.chart.ChartProjection;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

public class ChartProjector {
  public ChartProjection project(DatasetResult result) {
    if (result == null) {
      return new ChartProjection(null, null);
    }
    return new ChartProjection(result.getRows(), result.getResolvedSchema());
  }
}
