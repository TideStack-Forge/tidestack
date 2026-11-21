package com.ouroboros.dev.runtime.apisix.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApisixRoutePayload {
  private String id;
  private String uri;
  private List<String> hosts = new ArrayList<String>();
  private Upstream upstream = new Upstream();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }

  public List<String> getHosts() {
    return hosts;
  }

  public void setHosts(List<String> hosts) {
    this.hosts = hosts == null ? new ArrayList<String>() : hosts;
  }

  public Upstream getUpstream() {
    return upstream;
  }

  public void setUpstream(Upstream upstream) {
    this.upstream = upstream == null ? new Upstream() : upstream;
  }

  public static class Upstream {
    private String type = "roundrobin";
    private Map<String, Integer> nodes = new LinkedHashMap<String, Integer>();

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Map<String, Integer> getNodes() {
      return nodes;
    }

    public void setNodes(Map<String, Integer> nodes) {
      this.nodes = nodes == null ? new LinkedHashMap<String, Integer>() : nodes;
    }
  }
}
