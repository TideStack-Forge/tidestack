package com.ouroboros.logicflow.manage.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import jakarta.annotation.Priority;

import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Snapshot;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelMetaProvider;
import com.ouroboros.logicflow.model.FlowModelSource;

/** A database snapshot is a source in the original SPI, never an execution registry. */
@Component
public class DatabaseFlowModelMetaProvider implements FlowModelMetaProvider {
  public static final String SOURCE_ID = "runtime-database";
  public static final int PRIORITY = 200;
  private volatile FlowModelSource source;

  public DatabaseFlowModelMetaProvider() { Bridge.instance = this; }

  public void accept(Snapshot snapshot) { source = source(snapshot); }

  public static FlowModelSource source(Snapshot snapshot) {
    var definitions = new TreeMap<String, String>(String::compareToIgnoreCase);
    snapshot.overrides().values().stream().filter(row -> row.active())
        .forEach(row -> definitions.put(row.name(), row.definition()));
    return new FlowModelSource(SOURCE_ID, PRIORITY, true, true, true, List.of(),
        definitions, snapshot.version().token());
  }

  @Override public FlowModelSource observeSource() {
    var observed = source;
    if (observed == null) { throw new BusinessException(503, "逻辑流覆盖存储尚未初始化"); }
    return observed;
  }
  @Override public Map<String, FlowModelMeta> getFlowModelMetas() {
    return FlowModelDefinitions.thaw(observeSource().definitions());
  }
  @Override public Optional<FlowModelMeta> getFlowModelMeta(String name) {
    return getFlowModelMetas().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue).findFirst();
  }

  @Priority(PRIORITY)
  public static class Bridge implements FlowModelMetaProvider {
    private static volatile DatabaseFlowModelMetaProvider instance;
    private DatabaseFlowModelMetaProvider required() {
      var value = instance;
      if (value == null) { throw new BusinessException(503, "逻辑流覆盖来源尚未装配"); }
      return value;
    }
    @Override public FlowModelSource observeSource() { return required().observeSource(); }
    @Override public Map<String, FlowModelMeta> getFlowModelMetas() { return required().getFlowModelMetas(); }
    @Override public Optional<FlowModelMeta> getFlowModelMeta(String name) { return required().getFlowModelMeta(name); }
  }
}
