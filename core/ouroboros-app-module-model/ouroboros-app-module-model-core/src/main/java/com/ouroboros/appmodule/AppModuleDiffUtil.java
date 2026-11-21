package com.ouroboros.appmodule;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"unused", "SimplifyStreamApiCallChains"})
public class AppModuleDiffUtil {

  /**
   * 比较两个模块列表的差异
   *
   * @param originalModules 原始模块列表
   * @param newModules      修改后的模块列表
   * @return 模块差异列表
   */
  public static List<AppModuleDiff> diff(List<? extends AppModule> originalModules, List<? extends AppModule> newModules) {
    // 全名列表，用于对比删除了哪些模块
    var originalFullNameList = originalModules.stream()
        .map(AppModule::getFullName)
        .collect(Collectors.toList());
    var newFullNameList = newModules.stream()
        .map(AppModule::getFullName)
        .collect(Collectors.toList());
    // 原始模块列表，用于对比修改和增加的模块
    var originalModuleMap = originalModules.stream()
        .collect(() -> new TreeMap<String, AppModule>(), (map, module) -> map.put(module.getFullName(), module), TreeMap::putAll);

    // 删除的模块Diff
    var removedDiffList = originalFullNameList.stream().filter(f -> !newFullNameList.contains(f))
        .map(AppModuleDiff.Diff::new)
        .collect(Collectors.toList());

    // 修改和增加的模块Diff
    var otherDiffList = newModules.stream()
        .map(m -> m.diff(originalModuleMap.get(m.getFullName())))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());

    // 合并Diff
    return Stream.concat(removedDiffList.stream(), otherDiffList.stream())
        .collect(Collectors.toList());
  }

  /**
   * 根据差异列表，对原始模块列表进行补丁
   *
   * @param rawModules 原始模块列表
   * @param diffList   模块差异列表
   * @return 补丁后的模块列表
   */
  public static List<AppModule> patch(List<AppModule> rawModules, List<AppModuleDiff> diffList) {
    Map<String, AppModule> modulesByFullName = rawModules.stream()
        .collect(LinkedHashMap::new, (map, module) -> map.put(module.getFullName(), module), LinkedHashMap::putAll);

    diffList.stream()
        .filter(diff -> diff != null && diff.getFullName() != null)
        .forEach(diff -> applyDiff(modulesByFullName, diff));

    return modulesByFullName.values().stream()
        .sorted(Comparator.comparingInt(m -> Optional.ofNullable(m.getOrder()).orElse(0)))
        .collect(Collectors.toList());
  }

  private static void applyDiff(Map<String, AppModule> modulesByFullName, AppModuleDiff diff) {
    if (diff.getDiffType() == AppModuleDiff.DiffType.REMOVE) {
      modulesByFullName.remove(diff.getFullName());
      return;
    }
    if (diff.getDiffType() == AppModuleDiff.DiffType.ADD) {
      modulesByFullName.put(diff.getFullName(), diff.getAppModule());
      return;
    }
    if (diff.getDiffType() == AppModuleDiff.DiffType.MODIFY && modulesByFullName.containsKey(diff.getFullName())) {
      modulesByFullName.put(diff.getFullName(), diff.patch(modulesByFullName.get(diff.getFullName())));
    }
  }
}
