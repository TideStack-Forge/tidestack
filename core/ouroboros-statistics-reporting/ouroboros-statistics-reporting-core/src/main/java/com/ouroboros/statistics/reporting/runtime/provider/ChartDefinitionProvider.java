package com.ouroboros.statistics.reporting.runtime.provider;

import java.util.List;

import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

public interface ChartDefinitionProvider {

  List<ChartDefinition> getDefinitions();

  default int getOrder() {
    return 0;
  }
}
