package com.ouroboros.statistics.reporting.runtime.provider;

import java.util.List;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;

public interface DatasetDefinitionProvider {

  List<DatasetDefinition> getDefinitions();

  default int getOrder() {
    return 0;
  }
}
