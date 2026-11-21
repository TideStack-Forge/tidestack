package com.xinpai.spreadsheet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.service.OpenApiService;
import com.xinpai.spreadsheet.service.ReadSpreadsheetService;

class OpenApiExportTest {
  private MockMvc mvc;

  @BeforeEach
  void prepareExportData() {
    OpenApiService openApi = mock(OpenApiService.class);
    ReadSpreadsheetService reader = mock(ReadSpreadsheetService.class);
    SpreadsheetModel spreadsheet = new SpreadsheetModel();
    spreadsheet.setName("example");
    spreadsheet.setApp_id("example-app");
    spreadsheet.setIs_deleted(0);
    when(openApi.getSpreadsheetInfo("example-sheet")).thenReturn(spreadsheet);
    when(reader.getAllSheetBySpreadsheetId("example-sheet")).thenReturn(List.of(JSONObject.parseObject(
        "{\"name\":\"Sheet1\",\"celldata\":[{\"r\":0,\"c\":0,\"v\":\"exported value\"}]}")));
    OpenApiController controller = new OpenApiController();
    ReflectionTestUtils.setField(controller, "openApiService", openApi);
    ReflectionTestUtils.setField(controller, "readSpreadsheetService", reader);
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(new MappingJackson2HttpMessageConverter()).build();
  }

  @Test
  void jsonRequestExportsAnXlsxWorkbook() throws Exception {
    MockHttpServletResponse response = export("0");
    assertThat(response.getContentType()).isEqualTo(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(response.getHeader("Content-Disposition")).endsWith("example.xlsx");
    assertWorkbook(response.getContentAsByteArray());
  }

  @Test
  void zipOneWrapsTheWorkbookInOneNamedEntry() throws Exception {
    MockHttpServletResponse response = export("1");
    assertThat(response.getContentType()).isEqualTo("application/zip");
    assertThat(response.getHeader("Content-Disposition")).endsWith("example.zip");
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertThat(zip.getNextEntry().getName()).isEqualTo("example.xlsx");
      assertWorkbook(zip.readAllBytes());
      assertThat(zip.getNextEntry()).isNull();
    }
  }

  @Test
  void emptyJsonRequestReportsAMissingSpreadsheetId() throws Exception {
    mvc.perform(post("/api/open/export").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  private MockHttpServletResponse export(String zip) throws Exception {
    return mvc.perform(post("/api/open/export").contentType("application/json")
        .content("{\"appId\":\"example-app\",\"spreadsheetId\":\"example-sheet\",\"zip\":\"" + zip + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse();
  }

  private void assertWorkbook(byte[] bytes) throws Exception {
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
      assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("exported value");
    }
  }
}
