package com.ouroboros.businessmodel.logicflow.childcollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

public class SyncChildCollectionsNode extends AbstractNode<Object> {
  public static final String TYPE = "syncChildCollections";

  private final ChildCollectionSyncMode mode;
  private final List<ChildCollectionSyncPlan> plans;
  private final ChildCollectionSynchronizer synchronizer;

  public SyncChildCollectionsNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    this.mode = parseMode(meta);
    this.plans = parsePlans(meta);
    this.synchronizer = new ChildCollectionSynchronizer(DataModelCenter::getDataModel);
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    if (inputs.size() != 2) {
      throw new FlowRuntimeException("syncChildCollections 节点需要 persistedOwner 和 submittedOwner 两个输入", this, inputs);
    }
    if (!(inputs.get(0) instanceof Map<?, ?> persistedOwner) || !(inputs.get(1) instanceof Map<?, ?> submittedOwner)) {
      throw new FlowRuntimeException("syncChildCollections 节点输入必须是对象", this, inputs);
    }
    try {
      var result = synchronizer.sync(mode, copyMap(persistedOwner), copyMap(submittedOwner), plans);
      return Collections.singletonList(result);
    } catch (RuntimeException e) {
      throw new FlowRuntimeException("同步业务模型子表失败", e, this, inputs);
    }
  }

  @Override
  protected Integer minInputPortCount() {
    return 2;
  }

  @Override
  protected Integer maxInputPortCount() {
    return 2;
  }

  private ChildCollectionSyncMode parseMode(FlowNodeMeta meta) {
    return meta.getExtraProp(String.class, "mode")
        .map(ChildCollectionSyncMode::from)
        .orElseGet(() -> {
          addError(new NodeDesignException("syncChildCollections 节点缺少 mode", meta));
          return ChildCollectionSyncMode.REPLACE;
        });
  }

  private List<ChildCollectionSyncPlan> parsePlans(FlowNodeMeta meta) {
    var rawPlans = meta.getExtraProp("plans").orElse(null);
    if (!(rawPlans instanceof Collection<?> collection)) {
      addError(new NodeDesignException("syncChildCollections 节点缺少 plans", meta));
      return List.of();
    }
    return collection.stream()
        .map(this::parsePlan)
        .toList();
  }

  private ChildCollectionSyncPlan parsePlan(Object rawPlan) {
    if (rawPlan instanceof ChildCollectionSyncPlan plan) {
      return plan;
    }
    if (!(rawPlan instanceof Map<?, ?> rawMap)) {
      throw new IllegalArgumentException("syncChildCollections plans 项必须是对象");
    }
    var map = copyMap(rawMap);
    return new ChildCollectionSyncPlan(
        string(map, "relationName"),
        string(map, "ownerDataModelName"),
        string(map, "childDataModelName"),
        string(map, "ownerKeyField"),
        string(map, "childReferenceField"),
        string(map, "childPrimaryKeyField"),
        string(map, "ownerDataModelFingerprint"),
        string(map, "childDataModelFingerprint"),
        parseChildPlans(map.get("children"))
    );
  }

  private List<ChildCollectionSyncPlan> parseChildPlans(Object rawChildren) {
    if (rawChildren == null) {
      return List.of();
    }
    if (!(rawChildren instanceof Collection<?> collection)) {
      throw new IllegalArgumentException("syncChildCollections children 必须是集合");
    }
    var result = new ArrayList<ChildCollectionSyncPlan>();
    for (var rawChild : collection) {
      result.add(parsePlan(rawChild));
    }
    return result;
  }

  private static String string(Map<String, Object> map, String key) {
    var value = map.get(key);
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalArgumentException("syncChildCollections plan 缺少字段: " + key);
    }
    return string;
  }

  private static Map<String, Object> copyMap(Map<?, ?> source) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  public static class Builder implements NodeBuilder<SyncChildCollectionsNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return TYPE.equalsIgnoreCase(meta.getType());
    }

    @Override
    public SyncChildCollectionsNode build(
        FlowModel owner,
        FlowNodeMeta metadata,
        NodeReferenceCatalog references
    ) {
      return new SyncChildCollectionsNode(owner, metadata);
    }
  }
}
