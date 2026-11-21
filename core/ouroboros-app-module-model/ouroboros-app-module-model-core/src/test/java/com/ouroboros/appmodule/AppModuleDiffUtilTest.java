package com.ouroboros.appmodule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class AppModuleDiffUtilTest {

  @Test
  void shouldApplyDiffsSequentiallyByModuleFullName() {
    List<AppModule> patched = AppModuleDiffUtil.patch(
        List.of(module("System.A", "原始A", 20), module("System.B", "原始B", 30)),
        List.of(
            new AppModuleDiff.Diff(null, module("System.C", "第一次新增C", 10)),
            new AppModuleDiff.Diff(null, module("System.C", "第二次新增C", 10)),
            new AppModuleDiff.Diff(module("System.C", "第二次新增C", 10), module("System.C", "修改后的C", 10)),
            new AppModuleDiff.Diff("System.B"),
            new AppModuleDiff.Diff(null, module("System.B", "重新新增B", 30))
        )
    );

    Map<String, AppModule> modulesByFullName = patched.stream()
        .collect(Collectors.toMap(AppModule::getFullName, Function.identity()));

    assertEquals(3, patched.size());
    assertEquals("原始A", modulesByFullName.get("System.A").getTitle());
    assertEquals("修改后的C", modulesByFullName.get("System.C").getTitle());
    assertEquals("重新新增B", modulesByFullName.get("System.B").getTitle());
  }

  @Test
  void shouldCancelAddedModuleWhenItIsRemovedLater() {
    List<AppModule> patched = AppModuleDiffUtil.patch(
        List.of(),
        List.of(
            new AppModuleDiff.Diff(null, module("System.Temp", "临时模块", 10)),
            new AppModuleDiff.Diff("System.Temp")
        )
    );

    assertTrue(patched.isEmpty());
  }

  private AppModule module(String fullName, String title, int order) {
    return new BaseAppModule()
        .setFullName(fullName)
        .setTitle(title)
        .setOrder(order);
  }
}
