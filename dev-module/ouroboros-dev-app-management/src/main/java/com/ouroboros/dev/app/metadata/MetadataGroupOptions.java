package com.ouroboros.dev.app.metadata;

import java.util.List;
import java.util.TreeSet;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.web.SelectOption;

/** 按元数据的 group 路径派生选项，不维护独立的分类关系。 */
public final class MetadataGroupOptions {
  private MetadataGroupOptions() {
  }

  public static List<SelectOption> groups(List<Metadata> metadata) {
    var paths = new TreeSet<String>();
    for (var item : metadata) {
      String path = "";
      for (var segment : segments(item)) {
        path = path.isEmpty() ? segment : path + "/" + segment;
        paths.add(path);
      }
    }
    return paths.stream().map(path -> new SelectOption(path, path)).toList();
  }

  private static List<String> segments(Metadata metadata) {
    var group = metadata.getDetails() == null ? null : metadata.getDetails().get("group");
    if (!(group instanceof String path) || path.isBlank()) return List.of();
    return java.util.Arrays.stream(path.split("/")).map(String::strip)
        .filter(segment -> !segment.isEmpty()).toList();
  }
}
