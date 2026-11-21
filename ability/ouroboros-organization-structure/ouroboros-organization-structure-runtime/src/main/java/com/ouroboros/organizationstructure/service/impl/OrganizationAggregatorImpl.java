package com.ouroboros.organizationstructure.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.OrganizationNodeProvider;

@Component
public class OrganizationAggregatorImpl implements OrganizationAggregator {
  private final List<OrganizationNodeProvider> organizationNodeProviders;

  public OrganizationAggregatorImpl(ObjectProvider<OrganizationNodeProvider> organizationProvider) {
    this.organizationNodeProviders = organizationProvider.orderedStream()
        .collect(Collectors.toList());
  }

  @Override
  public Optional<OrganizationNode> getById(String id) {
    return organizationNodeProviders.stream()
        .map(organizationNodeProvider -> organizationNodeProvider.getById(id))
        .filter(Optional::isPresent)
        .map(node -> (OrganizationNode) node.get())
        .findAny();
  }

  @Override
  public List<OrganizationNode> getAll() {
    return organizationNodeProviders.stream()
        .map(OrganizationNodeProvider::getAll)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNode> getByIds(Collection<String> ids) {
    return getAll()
        .stream()
        .filter(employee -> ids.contains(employee.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<OrganizationNode> getDescendants(String id) {
    // 获取所有组织节点
    List<OrganizationNode> all = getAll();

    // 构建父节点ID到子节点列表的映射
    Map<String, List<OrganizationNode>> parentChildrenMap = new HashMap<>();
    for (OrganizationNode node : all) {
      // 跳过根节点（没有父节点）
      if (StringUtils.isEmpty(node.getParent())) {
        continue;
      }
      parentChildrenMap.computeIfAbsent(node.getParent(), k -> new ArrayList<>()).add(node);
    }

    List<OrganizationNode> descendantList = new ArrayList<>();
    Deque<String> queue = new ArrayDeque<>();
    //从当前节点开始BFS
    queue.add(id);

    // BFS遍历所有后代
    while (!queue.isEmpty()) {
      String currentId = queue.poll();
      List<OrganizationNode> children = parentChildrenMap.getOrDefault(currentId, Collections.emptyList());
      for (OrganizationNode child : children) {
        //添加子节点
        descendantList.add(child);
        //继续向下遍历
        queue.add(child.getId());
      }
    }

    return descendantList;
  }

  @Override
  public List<OrganizationNode> getAncestors(String id) {
    Optional<OrganizationNode> self = getById(id);
    List<OrganizationNode> ancestors = new ArrayList<>();
    if (!self.isPresent()) {
      return ancestors;
    }

    // 构建节点ID到节点对象的映射
    Map<String, OrganizationNode> nodeMap = new HashMap<>();
    for (OrganizationNode node : getAll()) {
      nodeMap.put(node.getId(), node);
    }

    // 从当前节点向上回溯查找祖先
    OrganizationNode currentNode = self.get();
    while (StringUtils.isNotEmpty(currentNode.getParent())) {
      OrganizationNode parent = nodeMap.get(currentNode.getParent());
      if (parent == null) {
        //父节点不存在时终止
        break;
      }

      //添加到祖先列表
      ancestors.add(parent);
      //继续向上查找
      currentNode = parent;
    }

    return ancestors;
  }
}
