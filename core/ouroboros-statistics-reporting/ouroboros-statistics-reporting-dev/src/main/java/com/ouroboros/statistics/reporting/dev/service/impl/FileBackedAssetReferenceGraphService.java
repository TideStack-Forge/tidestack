package com.ouroboros.statistics.reporting.dev.service.impl;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;


import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.statistics.reporting.dev.model.governance.AssetReferenceGraph;
import com.ouroboros.statistics.reporting.dev.service.AssetReferenceGraphService;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;

@Service
public class FileBackedAssetReferenceGraphService implements AssetReferenceGraphService {
  private static final String DATASET_TYPE = "dataset";
  private static final String CHART_TYPE = "chart";
  private static final String USES_DATASET = "uses-dataset";

  private final VCSManagerCenter vcsManagerCenter;

  public FileBackedAssetReferenceGraphService(VCSManagerCenter vcsManagerCenter) {
    this.vcsManagerCenter = vcsManagerCenter;
  }

  @Override
  public AssetReferenceGraph resolve(String appName) {
    AssetReferenceGraph graph = new AssetReferenceGraph();
    if (isBlank(appName)) {
      return graph;
    }

    Map<String, AssetReferenceGraph.Node> nodes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    List<AssetReferenceGraph.Edge> edges = new ArrayList<AssetReferenceGraph.Edge>();

    List<LowcodeDatasetDefinition> datasets = loadDefinitions(
        appName,
        DatasetDevService.MODEL_FILE_DIRECTORY,
        LowcodeDatasetDefinition.class
    );
    List<LowcodeChartDefinition> charts = loadDefinitions(
        appName,
        ChartDevService.MODEL_FILE_DIRECTORY,
        LowcodeChartDefinition.class
    );

    for (LowcodeDatasetDefinition dataset : datasets) {
      addNode(nodes, DATASET_TYPE, dataset.getName(), dataset.getTitle(), false);
    }
    for (LowcodeChartDefinition chart : charts) {
      addNode(nodes, CHART_TYPE, chart.getName(), chart.getTitle(), false);
    }

    for (LowcodeChartDefinition chart : charts) {
      appendDatasetEdge(nodes, edges, chart);
    }

    graph.setNodes(new ArrayList<AssetReferenceGraph.Node>(nodes.values()));
    graph.setEdges(edges);
    graph.setDangling(edges.stream()
        .filter(AssetReferenceGraph.Edge::isDangling)
        .collect(Collectors.toList()));
    return graph;
  }

  private <T> List<T> loadDefinitions(String appName, String directory, Class<T> modelClass) {
    return vcsManagerCenter.getVcsManager(appName).walkDirectory(directory, ".json")
        .map(file -> Json.tryToStrictBean(modelClass, file.getContent()).getOrElseThrow(error ->
            new com.ouroboros.exception.PlatformException("Failed to read statistics definition: " + file.getPath(), error)))
        .collect(Collectors.toList());
  }

  private void appendDatasetEdge(Map<String, AssetReferenceGraph.Node> nodes,
                                 List<AssetReferenceGraph.Edge> edges,
                                 ChartDefinition chart) {
    if (chart == null || isBlank(chart.getName()) || isBlank(chart.getDatasetName())) {
      return;
    }
    boolean dangling = !nodes.containsKey(nodeKey(DATASET_TYPE, chart.getDatasetName()));
    if (dangling) {
      addNode(nodes, DATASET_TYPE, chart.getDatasetName(), null, true);
    }
    edges.add(edge(
        DATASET_TYPE,
        chart.getDatasetName(),
        CHART_TYPE,
        chart.getName(),
        USES_DATASET,
        dangling
    ));
  }

  private AssetReferenceGraph.Node addNode(Map<String, AssetReferenceGraph.Node> nodes,
                                           String type,
                                           String name,
                                           String title,
                                           boolean dangling) {
    if (isBlank(type) || isBlank(name)) {
      return null;
    }
    String key = nodeKey(type, name);
    AssetReferenceGraph.Node existing = nodes.get(key);
    if (existing != null) {
      if (!dangling) {
        existing.setDangling(false);
      }
      if (!isBlank(title)) {
        existing.setTitle(title);
      }
      return existing;
    }

    AssetReferenceGraph.Node node = new AssetReferenceGraph.Node();
    node.setType(type);
    node.setName(name);
    node.setTitle(title);
    node.setDangling(dangling);
    nodes.put(key, node);
    return node;
  }

  private AssetReferenceGraph.Edge edge(String fromType,
                                        String fromName,
                                        String toType,
                                        String toName,
                                        String relation,
                                        boolean dangling) {
    AssetReferenceGraph.Edge edge = new AssetReferenceGraph.Edge();
    edge.setFromType(fromType);
    edge.setFromName(fromName);
    edge.setToType(toType);
    edge.setToName(toName);
    edge.setRelation(relation);
    edge.setDangling(dangling);
    return edge;
  }

  private String nodeKey(String type, String name) {
    return type + ":" + name;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
