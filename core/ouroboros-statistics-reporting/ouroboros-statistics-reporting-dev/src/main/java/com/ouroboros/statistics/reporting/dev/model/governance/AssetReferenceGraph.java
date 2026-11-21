package com.ouroboros.statistics.reporting.dev.model.governance;

import java.util.ArrayList;
import java.util.List;

public class AssetReferenceGraph {
  private List<Node> nodes = new ArrayList<Node>();
  private List<Edge> edges = new ArrayList<Edge>();
  private List<Edge> dangling = new ArrayList<Edge>();

  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(List<Node> nodes) {
    this.nodes = nodes == null ? new ArrayList<Node>() : nodes;
  }

  public List<Edge> getEdges() {
    return edges;
  }

  public void setEdges(List<Edge> edges) {
    this.edges = edges == null ? new ArrayList<Edge>() : edges;
  }

  public List<Edge> getDangling() {
    return dangling;
  }

  public void setDangling(List<Edge> dangling) {
    this.dangling = dangling == null ? new ArrayList<Edge>() : dangling;
  }

  public static class Node {
    private String type;
    private String name;
    private String title;
    private boolean dangling;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public boolean isDangling() {
      return dangling;
    }

    public void setDangling(boolean dangling) {
      this.dangling = dangling;
    }
  }

  public static class Edge {
    private String fromType;
    private String fromName;
    private String toType;
    private String toName;
    private String relation;
    private boolean dangling;

    public String getFromType() {
      return fromType;
    }

    public void setFromType(String fromType) {
      this.fromType = fromType;
    }

    public String getFromName() {
      return fromName;
    }

    public void setFromName(String fromName) {
      this.fromName = fromName;
    }

    public String getToType() {
      return toType;
    }

    public void setToType(String toType) {
      this.toType = toType;
    }

    public String getToName() {
      return toName;
    }

    public void setToName(String toName) {
      this.toName = toName;
    }

    public String getRelation() {
      return relation;
    }

    public void setRelation(String relation) {
      this.relation = relation;
    }

    public boolean isDangling() {
      return dangling;
    }

    public void setDangling(boolean dangling) {
      this.dangling = dangling;
    }
  }
}
