package com.ouroboros.businessmodel.dev.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.security.context.CurrentSecurityContextHolder;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.dev.DevAppModuleProvider;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.businessmodel.dev.service.BusinessModelModuleTreeService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.util.Trees;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Service
public class BusinessModelModuleTreeServiceImpl implements BusinessModelModuleTreeService, DevAppModuleProvider {
  final static String MODULE_FILE_NAME = "business-model/modules.json";
  @Autowired
  VCSManagerCenter vcsManagerCenter;
  @Autowired
  BusinessModelDevService businessModelDevService;

  private List<ModuleTreeNodePO> getModuleNodes() {
    var vcsManager = getVcsManager();
    var file = vcsManager.getFile(MODULE_FILE_NAME);

    // 如果未读取到内容则创建
    if (!file.exists()) {
      vcsManager.createFile(MODULE_FILE_NAME)
          .content("[]")
          .committer(getUserIdentity())
          .commit("创建模块树");
      return Collections.emptyList();
    }
    var fileContent = file.getContent();
    return Json.toList(new TypeReference<List<ModuleTreeNodePO>>() {
    }, fileContent.trim().isEmpty() ? "[]" : fileContent);
  }

  @Override
  public Try<Void> addModule(ModuleTreeNodeVO node) {
    var moduleTreeNodes = new ArrayList<>(getModuleNodes());
    return Try.run(() -> {
      if (moduleTreeNodes.stream().anyMatch(n -> node.getFullName().equals(n.getFullName()))) {
        throw new BusinessException("模块已存在");
      }
      var newPoNode = new ModuleTreeNodePO();
      newPoNode.setIcon(node.getIcon());
      newPoNode.setOrder(node.getOrder());
      newPoNode.setTitle(node.getTitle());
      newPoNode.setFullName(node.getFullName());
      moduleTreeNodes.add(newPoNode);
      writeModuleNodes(moduleTreeNodes);
    });
  }

  private boolean hasBusinessModel(String fullName) {
    return businessModelDevService.search(Collections.emptyMap())
        .stream()
        .filter(ModelFileMetaWrapper.class::isInstance)
        .map(ModelFileMetaWrapper.class::cast)
        .map(ModelFileMetaWrapper::getModel)
        .filter(BusinessModelMeta.class::isInstance)
        .map(BusinessModelMeta.class::cast)
        .map(BusinessModelMeta::getNamespace)
        .filter(StringUtils::isNotEmpty)
        .anyMatch(ns -> ns.startsWith(fullName));
  }

  @Override
  public Try<Void> deleteModule(String fullName) {
    var moduleTreeNodes = getModuleNodes();
    return Try.run(() -> {
      if (moduleTreeNodes.stream().map(ModuleTreeNodePO::getFullName).noneMatch(fullName::equals)) {
        return;
      }
      if (hasBusinessModel(fullName)) {
        throw new BusinessException("模块下存在模型，无法删除");
      }
      var newModuleNodes = moduleTreeNodes.stream()
          .filter(n -> !fullName.equals(n.getFullName()))
          .collect(Collectors.toList());
      writeModuleNodes(newModuleNodes);
    });
  }

  @Override
  public Try<Void> updateModule(String fullName, ModuleTreeNodeVO node) {
    var moduleTreeNodes = getModuleNodes();
    var newFullName = node.getFullName();
    return Try.run(() -> {
      if (moduleTreeNodes.stream().noneMatch(n -> fullName.equals(n.getFullName()))) {
        throw new BusinessException("模块已不存在");
      }
      if (!fullName.equals(newFullName) && moduleTreeNodes.stream().anyMatch(n -> node.getFullName().equals(n.getFullName()))) {
        throw new BusinessException("模块已存在");
      }
      if (fullName.equals(newFullName)) {
        moduleTreeNodes.stream()
            .filter(n -> fullName.equals(n.getFullName()))
            .findFirst()
            .ifPresent(n -> {
              n.setTitle(node.getTitle());
              n.setOrder(node.getOrder());
              n.setIcon(node.getIcon());
            });
        writeModuleNodes(moduleTreeNodes);
        return;
      }

      // 移动
      var po = new ModuleTreeNodePO();
      po.setFullName(node.getFullName());
      po.setTitle(node.getTitle());
      po.setOrder(node.getOrder());
      po.setIcon(node.getIcon());
      var newList = moduleTreeNodes.stream()
          .filter(n -> !n.getFullName().equals(fullName))
          .collect(Collectors.toList());
      newList.add(po);
      writeModuleNodes(newList);
    });
  }

  @Override
  public Try<List<ModuleTreeNodeVO>> getModuleTree() {
    var list = getModuleNodes();
    var roots = list.stream()
        .filter(n -> !n.getFullName().contains("."))
        .sorted(Comparator.comparingInt(ModuleTreeNodePO::getOrder))
        .collect(Collectors.toList());
    Function<ModuleTreeNodePO, List<ModuleTreeNodePO>> childrenGetter = (node) -> {
      final var parentName = node.getFullName();
      final var parentNameLength = parentName.length();
      return list.stream()
          .filter(child -> child.getFullName().startsWith(parentName + "."))
          .filter(child -> !child.getFullName().substring(parentNameLength + 1).contains("."))
          .sorted(Comparator.comparingInt(ModuleTreeNodePO::getOrder))
          .collect(Collectors.toList());
    };
    var tree = Trees.<ModuleTreeNodePO, ModuleTreeNodeVO>buildTree(roots, childrenGetter, (node, children) -> {
      var voNode = new ModuleTreeNodeVO();
      var lastDotIndex = node.getFullName().lastIndexOf(".");
      voNode.setFullName(node.getFullName());
      voNode.setParentName(lastDotIndex == -1 ? "" : node.getFullName().substring(0, lastDotIndex));
      voNode.setName(lastDotIndex == -1 ? node.getFullName() : node.getFullName().substring(lastDotIndex + 1));
      voNode.setTitle(node.getTitle());
      voNode.setOrder(node.getOrder());
      voNode.setIcon(node.getIcon());
      voNode.setChildren(children);
      return voNode;
    });
    return Try.success(tree);
  }

  private void writeModuleNodes(List<ModuleTreeNodePO> treeNodes) {
    var user = getUserIdentity();
    var vcsManager = getVcsManager();
    vcsManager.updateFile(MODULE_FILE_NAME)
        .content(Json.toPrettyJsonString(treeNodes))
        .committer(user)
        .commit("修改模块树");
  }

  private VCSManager getVcsManager() {
    return vcsManagerCenter.getVcsManager(DevAppHolder.getAppName());
  }

  private String getUserIdentity() {
    return CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity();
  }

  @Override
  public List<AppModule> getAppModules(String appName) {
    return (List) getModuleNodes();
  }
}
