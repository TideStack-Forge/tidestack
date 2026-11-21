package com.ouroboros.businessmodel.dev.vo;

import java.util.List;
import java.util.Map;

public record BusinessModelCanvas(String id, String title, boolean allModels, boolean grouped,
    List<String> modelIds, Layout freeLayout, Layout groupedLayout, String revision) {

  public record Point(double x, double y) {}

  public record Group(double x, double y, double width, double height, String title, String parent) {}

  public record Viewport(double zoom, Point offset) {}

  public record Layout(Map<String, Point> nodes, Map<String, Group> groups, Viewport viewport) {}
}
