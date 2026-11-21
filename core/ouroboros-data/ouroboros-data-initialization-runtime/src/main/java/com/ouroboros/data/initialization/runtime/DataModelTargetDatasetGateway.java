package com.ouroboros.data.initialization.runtime;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.initialization.TargetDatasetGateway;
import com.ouroboros.data.initialization.TargetDatasetRow;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.RecordList;

public class DataModelTargetDatasetGateway implements TargetDatasetGateway {
  @Override
  public Optional<TargetDatasetRow> findOne(String targetModel, Map<String, Object> matchValues) {
    RecordList records = model(targetModel).query(Map.of(Keyword.WHERE.toString(), matchValues))
        .getOrElseThrow(cause -> failure("查询初始化目标模型失败: " + targetModel, cause));
    if (records.size() > 1) {
      throw new IllegalStateException("初始化目标模型匹配结果不唯一: " + targetModel + ", matchValues=" + matchValues);
    }
    if (records.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new TargetDatasetRow(records.get(0)));
  }

  @Override
  public void insert(String targetModel, Map<String, Object> values) {
    model(targetModel).insert(values)
        .getOrElseThrow(cause -> failure("插入初始化目标模型失败: " + targetModel, cause));
  }

  private DataModel model(String targetModel) {
    return DataModelCenter.getDataModel(targetModel)
        .orElseThrow(() -> new IllegalStateException("初始化目标模型不存在: " + targetModel));
  }

  private RuntimeException failure(String message, Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new IllegalStateException(message, cause);
  }
}
