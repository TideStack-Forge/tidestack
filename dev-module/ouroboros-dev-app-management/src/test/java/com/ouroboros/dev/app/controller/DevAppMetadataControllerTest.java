package com.ouroboros.dev.app.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevAppMetadataControllerTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    MetadataProvider jar = (app, module) -> List.of(
        model("member", "读取成员", "钉钉/通讯录", 0),
        model("message", "发送消息", "钉钉/消息", 0),
        model("lookup", "旧查询", "旧分组", 0));
    MetadataProvider application = (app, module) -> List.of(
        model("lookup", "订单查询", "业务/订单", 10),
        model("legacy", "未分类接口", null, 10),
        model("literal", "普通接口", "单层分组", 10));
    mvc = MockMvcBuilders.standaloneSetup(new DevAppMetadataController(List.of(jar, application))).build();
  }

  @Test
  void flatSelectAndRawModesKeepTheirExistingContracts() throws Exception {
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("value", "member"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].value").value("member"))
        .andExpect(jsonPath("$.data.items[0].label").value("读取成员"))
        .andExpect(jsonPath("$.data.items[0].children").doesNotExist());
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("mode", "raw").param("value", "member"))
        .andExpect(jsonPath("$.data.items[0].group").value("钉钉/通讯录"));
  }

  @Test
  void rawModelsMergeJarAndApplicationOverridesAndPreserveModelNames() throws Exception {
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("mode", "raw"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[*].name").value(org.hamcrest.Matchers.containsInAnyOrder(
            "member", "message", "lookup", "legacy", "literal")))
        .andExpect(jsonPath("$.data.items[?(@.name == 'member')].title").value("读取成员"))
        .andExpect(jsonPath("$.data.items[?(@.name == 'member')].group").value("钉钉/通讯录"))
        .andExpect(jsonPath("$.data.items[?(@.name == 'lookup')].title").value("订单查询"))
        .andExpect(jsonPath("$.data.items[?(@.name == 'lookup')].group").value("业务/订单"));
  }

  @Test
  void groupSuggestionsContainDistinctFullPathsAndAncestorsAfterProviderOverrides() throws Exception {
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("mode", "groups"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[*].value").value(org.hamcrest.Matchers.containsInAnyOrder(
            "钉钉", "钉钉/通讯录", "钉钉/消息", "业务", "业务/订单", "单层分组")))
        .andExpect(jsonPath("$.data.items[*].label").value(org.hamcrest.Matchers.containsInAnyOrder(
            "钉钉", "钉钉/通讯录", "钉钉/消息", "业务", "业务/订单", "单层分组")));
  }

  @Test
  void rawSearchAndSelectedValueReloadKeepMatchingModelDetails() throws Exception {
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("mode", "raw").param("keyword", "成员"))
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("member"))
        .andExpect(jsonPath("$.data.items[0].title").value("读取成员"));
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("mode", "raw")
            .param("keyword", "不会匹配").param("value", "message"))
        .andExpect(jsonPath("$.data.items[0].name").value("message"))
        .andExpect(jsonPath("$.data.items[0].group").value("钉钉/消息"));
    mvc.perform(get("/api/dev/demo/metadata/rpc-model").param("mode", "raw").param("keyword", "不存在"))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  private static Metadata model(String name, String title, String group, int priority) {
    var metadata = new Metadata();
    metadata.setName(name);
    metadata.setLabel(title);
    metadata.setPriority(priority);
    metadata.setDetails(group == null ? Map.of("name", name, "title", title)
        : Map.of("name", name, "title", title, "group", group));
    return metadata;
  }
}
