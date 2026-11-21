package com.ouroboros.logicflow.model;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import com.ouroboros.logicflow.support.LogicFlowServices;

public class FlowModelMetaCenter {

  public static FlowModelCatalogue observe() {
    return new FlowModelCatalogue(LogicFlowServices.cachedSortedServiceStream(FlowModelMetaProvider.class)
        .map(FlowModelMetaProvider::observeSource).toList());
  }

  private static Function<String, Optional<FlowModelMeta>> FLOW_MODEL_META_PROVIDER_CHAIN = LogicFlowServices.cachedReversedServiceStream(FlowModelMetaProvider.class)
      .map(f -> (Function<String, Optional<FlowModelMeta>>) f::getFlowModelMeta)
      .reduce(name -> Optional.empty(), (f1, f2) -> name -> {
        var result = f1.apply(name);
        if (result.map(FlowModelMeta::isEnabled).orElse(false)) {
          return result;
        }
        return f2.apply(name);
      });

  public static Optional<FlowModelMeta> getFlowModel(String name) {
    return FLOW_MODEL_META_PROVIDER_CHAIN.apply(name);
  }

  public static Map<String, FlowModelMeta> getFlowModels() {
    return LogicFlowServices.cachedSortedServiceStream(FlowModelMetaProvider.class)
        .map(provider -> provider.getFlowModelMetas())
        .reduce(new TreeMap<>(String::compareToIgnoreCase), (map1, map2) -> {
          map2.forEach((k, v) -> {
            if (v.isEnabled()) {
              map1.put(k, v);
            }
          });
          return map1;
        });
  }
}
