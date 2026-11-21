package com.ouroboros.data.initialization.runtime;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.initialization.DataInitializationAsset;
import com.ouroboros.data.initialization.InitializationBlock;
import com.ouroboros.data.initialization.InitializationBlockSummary;
import com.ouroboros.data.initialization.InitializationLedger;
import com.ouroboros.data.initialization.InitializationRun;
import com.ouroboros.data.initialization.LedgerKey;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.util.Json;

public class DataModelInitializationLedger implements InitializationLedger {
  static final String MODEL_NAME = "DataInitializationLedgerRecord";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_FAILED = "FAILED";

  @Override
  public InitializationRun begin(DataInitializationAsset asset, InitializationBlock block, LedgerKey key) {
    String id = id(key);
    return existingRun(id, asset, block, key)
        .orElseGet(() -> insertRunningOrReadConcurrent(asset, block, key, id));
  }

  private Optional<InitializationRun> existingRun(String id, DataInitializationAsset asset, InitializationBlock block,
                                                  LedgerKey key) {
    RecordList existing = model().query(Map.of(Keyword.WHERE.toString(), Map.of("id", id)))
        .getOrElseThrow(cause -> failure("查询数据初始化账本开始记录失败", cause));
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    updateOne(id, runningValues(asset, block, key), "更新数据初始化账本开始记录失败");
    return Optional.of(new InitializationRun(id, key));
  }

  private InitializationRun insertRunningOrReadConcurrent(DataInitializationAsset asset, InitializationBlock block,
                                                          LedgerKey key, String id) {
    try {
      model().insert(runningValues(asset, block, key))
          .getOrElseThrow(cause -> failure("写入数据初始化账本开始记录失败", cause));
      return new InitializationRun(id, key);
    } catch (RuntimeException ex) {
      return existingRun(id, asset, block, key).orElseThrow(() -> ex);
    }
  }

  @Override
  public void succeed(InitializationRun run, InitializationBlockSummary summary) {
    updateOne(run.id(), Map.of(
            "status", STATUS_SUCCESS,
            "finishedAt", LocalDateTime.now(),
            "summary", Map.of(
                "inserted", summary.inserted(),
                "skipped", summary.skipped()
            )
        ), "写入数据初始化账本成功记录失败");
  }

  @Override
  public void fail(InitializationRun run, Throwable cause) {
    updateOne(run.id(), Map.of(
            "status", STATUS_FAILED,
            "finishedAt", LocalDateTime.now(),
            "errorMessage", cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()
        ), "写入数据初始化账本失败记录失败");
  }

  private Map<String, Object> runningValues(DataInitializationAsset asset, InitializationBlock block, LedgerKey key) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", id(key));
    values.put("planId", key.planId());
    values.put("blockId", key.blockId());
    values.put("sourceKind", asset.sourceKind());
    values.put("sourceLocation", asset.sourceLocation());
    values.put("targetModel", key.targetModel());
    values.put("assetVersion", key.assetVersion());
    values.put("contentHash", key.contentHash());
    values.put("status", STATUS_RUNNING);
    values.put("startedAt", LocalDateTime.now());
    values.put("finishedAt", null);
    values.put("summary", null);
    values.put("errorMessage", null);
    return values;
  }

  private void updateOne(String id, Map<String, Object> values, String errorMessage) {
    Long affectedRows = model().update(id, values).getOrElseThrow(cause -> failure(errorMessage, cause));
    if (affectedRows != 1L) {
      throw new IllegalStateException(errorMessage + ": id=" + id + ", affectedRows=" + affectedRows);
    }
  }

  private String id(LedgerKey key) {
    Map<String, Object> material = new LinkedHashMap<>();
    material.put("planId", key.planId());
    material.put("blockId", key.blockId());
    material.put("targetModel", key.targetModel());
    material.put("assetVersion", key.assetVersion());
    material.put("contentHash", key.contentHash());
    return DataInitializationRuntimeHashes.sha256(Json.toJsonString(material));
  }

  private DataModel model() {
    return DataModelCenter.getDataModel(MODEL_NAME)
        .orElseThrow(() -> new IllegalStateException("数据初始化账本模型不存在: " + MODEL_NAME));
  }

  private RuntimeException failure(String message, Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new IllegalStateException(message, cause);
  }
}
