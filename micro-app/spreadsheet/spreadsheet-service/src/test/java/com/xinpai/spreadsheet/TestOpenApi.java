package com.xinpai.spreadsheet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

@SpringBootTest
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
public class TestOpenApi {
  @Autowired
  private MockMvc mockMvc;

  @Before
  public void setup() {
  }

  @Test
  public void testCreateSpreadsheet() throws Exception {
    String json = "{\"appId\":\"459648aa6821000\",\"editorName\":\"张三\",\"appSecret\":\"9289a2b109eb8f450cef43ca5e259e48da4d72ce\",\"spreadsheetName\":\"测试表格\"}";
    mockMvc.perform(
        MockMvcRequestBuilders
            .post("/api/open/create")
            .contentType("application/json;charset=UTF-8").content(json))
        .andDo(MockMvcResultHandlers.print())
        .andReturn();
  }

  @Test
  public void testRenameSpreadsheet() throws Exception {
    String json = "{\"appId\":\"459648aa6821000\",\"editorName\":\"张三\",\"appSecret\":\"9289a2b109eb8f450cef43ca5e259e48da4d72ce\",\"spreadsheetId\":\"459abcaef821000\",\"spreadsheetName\":\"改名测试表格\"}";
    mockMvc.perform(
        MockMvcRequestBuilders
            .post("/api/open/rename")
            .contentType("application/json;charset=UTF-8").content(json))
        .andDo(MockMvcResultHandlers.print())
        .andReturn();
  }

  @Test
  public void testGetEditToken() throws Exception {
    String json = "{\"appId\":\"459648aa6821000\",\"editorName\":\"张三\",\"appSecret\":\"9289a2b109eb8f450cef43ca5e259e48da4d72ce\",\"spreadsheetId\":\"459abcaef821000\"}";
    mockMvc.perform(
        MockMvcRequestBuilders
            .post("/api/open/token")
            .contentType("application/json;charset=UTF-8").content(json))
        .andDo(MockMvcResultHandlers.print())
        .andReturn();

    json = "{\"appId\":\"459648aa6821000\",\"editorName\":\"张三\",\"appSecret\":\"9289a2b109eb8f450cef43ca5e259e48da4d72ce\",\"spreadsheetId\":\"455a50a20ea1000\"}";
    mockMvc.perform(
        MockMvcRequestBuilders
            .post("/api/open/token")
            .contentType("application/json;charset=UTF-8").content(json))
        .andDo(MockMvcResultHandlers.print())
        .andReturn();
  }

  @Test
  public void testDeleteSpreadsheet() throws Exception {
    String json = "{\"appId\":\"459648aa6821000\",\"editorName\":\"删除人\",\"appSecret\":\"9289a2b109eb8f450cef43ca5e259e48da4d72ce\",\"spreadsheetId\":\"459abcaef821000\"}";
    mockMvc.perform(
        MockMvcRequestBuilders
            .post("/api/open/delete")
            .contentType("application/json;charset=UTF-8").content(json))
        .andDo(MockMvcResultHandlers.print())
        .andReturn();
  }

  @Test
  public void testGetInfo() throws Exception {
    String json = "{\"appId\":\"459648aa6821000\",\"appSecret\":\"9289a2b109eb8f450cef43ca5e259e48da4d72ce\",\"spreadsheetId\":\"459abcaef821000\"}";
    mockMvc.perform(
        MockMvcRequestBuilders
            .post("/api/open/info")
            .contentType("application/json;charset=UTF-8").content(json))
        .andDo(MockMvcResultHandlers.print())
        .andReturn();
  }

}