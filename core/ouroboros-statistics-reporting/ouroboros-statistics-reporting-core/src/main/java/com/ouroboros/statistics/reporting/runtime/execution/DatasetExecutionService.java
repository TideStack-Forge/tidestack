package com.ouroboros.statistics.reporting.runtime.execution;

import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

public interface DatasetExecutionService {

  DatasetResult execute(DatasetExecutionRequest request);

  default DatasetResult execute(com.ouroboros.statistics.reporting.definition.DatasetDefinition definition,
                                DatasetExecutionRequest request) {
    throw new com.ouroboros.exception.PlatformException("当前数据集执行服务不支持私有查询");
  }
}
