package com.ouroboros.logicflow.model;

import java.util.Map;
import java.util.UUID;

/** Models and their exact metadata/provenance are switched together. Strings are immutable. */
public record FlowModelPublication(String id, Map<String, FlowModel> models,
    Map<String, String> definitions, FlowModelCatalogue catalogue, Map<String, String> modelSourceStamps,
    boolean complete) {
  public FlowModelPublication {
    models = Map.copyOf(models);
    definitions = Map.copyOf(definitions);
    modelSourceStamps = Map.copyOf(modelSourceStamps);
  }

  public static FlowModelPublication completed(Map<String, FlowModel> models, FlowModelCatalogue catalogue) {
    var stamps = new java.util.LinkedHashMap<String, String>();
    models.keySet().forEach(name -> stamps.put(name, catalogue.stamp()));
    return new FlowModelPublication(UUID.randomUUID().toString(), models, catalogue.definitions(),
        catalogue, stamps, true);
  }
}
