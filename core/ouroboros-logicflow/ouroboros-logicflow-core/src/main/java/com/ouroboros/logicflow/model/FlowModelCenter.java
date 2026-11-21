package com.ouroboros.logicflow.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.support.LogicFlowServices;

public final class FlowModelCenter {
  private static List<FlowModelProvider> providers = LogicFlowServices.cachedReversedServiceStream(FlowModelProvider.class).collect(Collectors.toList());

  // 以前这块不支持热刷新，要支持刷新要分两步走：
  // 1. 逻辑流执行每次都从这里取，这样就可以在这里做缓存
  // 2. 监听刷新事件重建缓存
  // 要支持监听刷新事件，就要做成 Spring Bean，考虑添加 FlowModelProvider SPI，在runtime包中实现
  public static Optional<FlowModel> getFlowModel(String flowName) {
    return providers.stream()
        .map(provider -> provider.getFlowModel(flowName))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(FlowModel::isEnabled)
        .findFirst();
  }

  /** The same winning execution provider used by getFlowModel, for optional management capabilities. */
  public static Optional<FlowModelProvider> providerFor(String flowName) {
    return providers.stream().filter(provider -> provider.getFlowModel(flowName)
        .filter(FlowModel::isEnabled).isPresent()).findFirst();
  }

  public static Map<String, FlowModel> getFlowModelMap() {
    Map<String, FlowModel> flowModelMap = new HashMap<>();
    providers.stream()
        .map(FlowModelProvider::getFlowModelMap)
        .forEach(m -> m.forEach((k, v) -> {
          if (v.isEnabled()) {
            flowModelMap.putIfAbsent(k, v);
          }
        }));
    return flowModelMap;
  }
}
