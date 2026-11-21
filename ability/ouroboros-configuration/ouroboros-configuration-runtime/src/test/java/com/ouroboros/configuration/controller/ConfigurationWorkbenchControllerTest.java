package com.ouroboros.configuration.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.ouroboros.Application;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.service.impl.ConfigurationWorkbenchServiceImpl;
import com.ouroboros.data.model.TypedDataModel;

import io.vavr.control.Try;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Application.class})
@AutoConfigureMockMvc
public class ConfigurationWorkbenchControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TypedDataModel<String, RuntimeConfigurationValueModel> configurationValueDataModel;

  @Before
  public void setUp() {
    when(configurationValueDataModel.query(anyMap())).thenReturn(Try.success(List.of()));
  }

  @Test
  public void tree_returnsWorkbenchNodes() throws Exception {
    mockMvc.perform(get("/api/sys/configuration/workbench/tree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[*].path", hasItems("security", ConfigurationWorkbenchServiceImpl.UNGROUPED_PATH)));
  }

  @Test
  public void group_returnsGroupPanel() throws Exception {
    mockMvc.perform(get("/api/sys/configuration/workbench/group").param("path", "security"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("安全设置"))
        .andExpect(jsonPath("$.data.childGroups[*].path", hasItems("security.auth", "security.session")));
  }

  @Test
  public void item_returnsItemPanel() throws Exception {
    mockMvc.perform(get("/api/sys/configuration/workbench/item/test0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.label").value("Test 0"))
        .andExpect(jsonPath("$.data.groupPath").value("security.session"));
  }
}
