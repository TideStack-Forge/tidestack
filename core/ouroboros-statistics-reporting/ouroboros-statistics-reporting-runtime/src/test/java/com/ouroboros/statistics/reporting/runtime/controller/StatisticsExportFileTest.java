package com.ouroboros.statistics.reporting.runtime.controller;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.FileResponses;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsExportFileTest {
  @Test
  void generatesACompleteWorkbookThroughThePlatformFileResponseAndReleasesTheFile() throws Exception {
    var result = StatisticsExportFile.create("订单统计", Map.of("columns", List.of(
        Map.of("name", "id", "title", "订单编号"), Map.of("name", "amount", "title", "金额")),
        "rows", List.of(Map.of("id", "A01", "amount", 120), Map.of("id", "A02", "amount", 80))));
    var response = new MockHttpServletResponse();
    FileResponses.write(result, response);
    assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.getContentType());
    assertTrue(response.getHeader("Content-Disposition").contains(".xlsx"));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      var sheet = workbook.getSheetAt(0);
      assertEquals(2, sheet.getLastRowNum());
      assertEquals("订单编号", sheet.getRow(0).getCell(0).getStringCellValue());
      assertEquals("A02", sheet.getRow(2).getCell(0).getStringCellValue());
      assertEquals(80, sheet.getRow(2).getCell(1).getNumericCellValue());
    }
    assertThrows(java.io.IOException.class, result::openStream);
  }

  @Test
  void servesJsonAndExcelThroughOneRegisteredHttpOperation() throws Exception {
    var dataset = new com.ouroboros.statistics.reporting.definition.DatasetDefinition();
    dataset.setName("demo.orders"); dataset.setIsEnabled(true);
    var field = new com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition();
    field.setName("id"); field.setTitle("编号"); field.setDataType("string"); field.setExportable(true);
    var schema = new com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition(); schema.setFields(List.of(field)); dataset.setSchema(schema);
    var center = new com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter(List.of(() -> List.of(dataset)));
    var controller = new DatasetExecutionController(center, request -> {
      var result = new com.ouroboros.statistics.reporting.runtime.result.DatasetResult();
      result.setResolvedSchema(schema); result.setRows(List.of(Map.of("id", "A01")));
      return result;
    }, (definition, request) -> true, request -> Map.of());
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var json = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
        "/api/statistics-reporting/datasets/demo.orders/export").contentType("application/json").content("{}"))
        .andReturn().getResponse().getContentAsString();
    assertEquals("A01", com.ouroboros.util.Json.mapperCopy().readTree(json).at("/data/rows/0/id").asText());
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
        "/api/statistics-reporting/datasets/demo.orders/export?format=xlsx").contentType("application/json").content("{}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
    assertTrue(response.getContentType().contains("spreadsheetml"));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertEquals("A01", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
    }
  }

  @Test
  void rejectsAnExportWithNoPermittedColumnsBeforeCreatingAFile() {
    assertThrows(BusinessException.class, () -> StatisticsExportFile.create("empty", Map.of("columns", List.of(), "rows", List.of())));
  }
}
