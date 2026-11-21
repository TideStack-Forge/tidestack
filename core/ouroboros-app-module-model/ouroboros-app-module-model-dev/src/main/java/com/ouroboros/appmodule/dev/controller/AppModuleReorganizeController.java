package com.ouroboros.appmodule.dev.controller;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleDiff;
import com.ouroboros.appmodule.AppModuleDiffUtil;
import com.ouroboros.appmodule.dev.AppModuleAggregator;
import com.ouroboros.appmodule.dev.controller.vo.FormInitDataVO;
import com.ouroboros.appmodule.dev.controller.vo.RemovedModuleVO;
import com.ouroboros.appmodule.dev.controller.vo.TreeNodeVO;
import com.ouroboros.appmodule.model.AppModuleTreeNode;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.modelmanage.web.AbstractSingleFileController;
import com.ouroboros.util.Json;
import com.ouroboros.util.Trees;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/app-module")
@SuppressWarnings("unused")
public class AppModuleReorganizeController extends AbstractSingleFileController<List<TreeNodeVO>, FormInitDataVO> {
  @Autowired
  private AppModuleAggregator appModuleAggregator;

  @Override
  protected String getSingleFilePath() {
    return "modules.diff.json";
  }

  @Override
  protected Try<FormInitDataVO> getInitFormData(VCSFile file) {
    var formData = getTree().getData();
    if (formData != null) {
      return Try.success(formData);
    }
    return Try.failure(new PlatformException("获取初始化数据失败"));
  }

  @Override
  protected Try<List<TreeNodeVO>> getSubmitModelFromJson(String jsonString) {
    var moduleTreeTry = Json.tryToList(new TypeReference<List<TreeNodeVO>>() {
    }, jsonString);
    if (moduleTreeTry.isFailure()) {
      return Try.failure(new BusinessException("解析提交数据失败"));
    }

    return moduleTreeTry;
  }

  @Override
  protected Try<String> getPermanentJson(List<TreeNodeVO> moduleTree) {
    TreeNodeVO.reorder(moduleTree);
    moduleTree.forEach(m -> {
      if (!Objects.isNull(m.getParent())) {
        m.setParent("");
      }
    });
    var newAppModules = Trees.flattenTree(moduleTree, TreeNodeVO::getChildren);
    var appModules = appModuleAggregator.getAppModules();
    var diffs = AppModuleDiffUtil.diff(appModules, newAppModules);
    var diffJson = Json.tryToPrettyJsonString(diffs);
    if (diffJson.isFailure()) {
      return Try.failure(new BusinessException("序列化"));
    }
    return diffJson;
  }

  @Override
  protected String getDefaultCommitMsg() {
    return getSingleFile().exists()
        ? "更新模块编排文件"
        : "创建模块编排文件";
  }

  @GetMapping("/raw-tree")
  public ItemsWebApiResult<AppModuleTreeNode> getRawTree() {
    var appModules = appModuleAggregator.getAppModules();
    return ItemsWebApiResult.ok(buildTree(appModules));
  }

  @GetMapping("")
  public WebApiResult<FormInitDataVO> getTree() {
    var appModules = appModuleAggregator.getAppModules();
    var diffs = appModuleAggregator.getDiffs();

    // 原始模块列表 + 补丁列表 = 补丁后的模块列表
    var finalAppModules = AppModuleDiffUtil.patch(appModules, diffs);

    // 找到删除的模块，提供给前端（用于恢复已删除模块）
    var removedAppModules = diffs.stream()
        .filter(d -> d.getDiffType() == AppModuleDiff.DiffType.REMOVE)
        .map(diff -> appModules.stream()
            .filter(m -> m.getFullName().equalsIgnoreCase(diff.getFullName()))
            .findFirst()
            .orElse(null)
        )
        .filter(Objects::nonNull)
        .map(RemovedModuleVO::new)
        .collect(Collectors.toList());

    // 构造树型结构
    var moduleTree = buildTree(finalAppModules);

    var result = new FormInitDataVO(moduleTree, removedAppModules);
    return WebApiResult.ok(result);
  }

  @SuppressWarnings("SimplifyStreamApiCallChains")
  private List<AppModuleTreeNode> buildTree(List<AppModule> moduleList) {
    // 先把所有模块转换为 TreeNode
    var treeNodeList = moduleList.stream()
        .map(AppModuleTreeNode::new)
        .collect(Collectors.toList());

    // 找到所有根
    var roots = treeNodeList.stream()
        .filter(m -> ObjectUtils.isEmpty(m.getParent()))
        .collect(Collectors.toList());

    // 子节点查找器
    Function<AppModuleTreeNode, List<AppModuleTreeNode>> childrenFinder = node ->
        treeNodeList.stream()
            .filter(m -> node.getFullName().equals(m.getParent()))
            .collect(Collectors.toList());

    // 构造树
    return Trees.buildTree(roots, childrenFinder, AppModuleTreeNode::setChildren);
  }

}
