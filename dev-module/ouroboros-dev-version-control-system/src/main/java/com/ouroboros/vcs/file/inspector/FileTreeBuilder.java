package com.ouroboros.vcs.file.inspector;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.util.SPI;
import com.ouroboros.util.Trees;
import com.ouroboros.vcs.file.VCSFile;

public final class FileTreeBuilder {
  private static final List<VCSFileInspector> INSPECTORS = SPI.getReversedServiceStream(VCSFileInspector.class)
      .collect(Collectors.toList());
  public static List<? extends DescriptionNode> build(Collection<VCSFile> files) {
    var dirs = files.stream()
        .flatMap(file -> INSPECTORS.stream()
            .flatMap(inspector -> inspector.inspectDir(file).stream())
        )
        .distinct()
        .collect(Collectors.toList());
    var fileDescList = files.stream()
        .map(file -> INSPECTORS.stream()
            .map(inspector -> inspector.inspect(file))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
        )
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
    var allNodes = new ArrayList<DescriptionNode>();
    allNodes.addAll(dirs);
    allNodes.addAll(fileDescList);
    var roots = allNodes.stream()
        .filter(node -> StringUtils.isEmpty(node.getParent()))
        .collect(Collectors.toList());
    var otherNodes = new ArrayList<DescriptionNode>();
    var tree = Trees.<DescriptionNode, DescriptionNode>buildTree(roots, node -> allNodes.stream()
        .filter(item -> Objects.equals(item.getParent(), node.getPath()))
        .collect(Collectors.toList()),
        (node, children) -> {
          // if node is dir
          if (node instanceof DirDescription dirNode) {
            dirNode.setChildren(children);
          }
          else {
            otherNodes.addAll(children);
          }
          return node;
        });
    if (!otherNodes.isEmpty()) {
      tree.addAll(otherNodes);
    }
    return tree;
  }
}
