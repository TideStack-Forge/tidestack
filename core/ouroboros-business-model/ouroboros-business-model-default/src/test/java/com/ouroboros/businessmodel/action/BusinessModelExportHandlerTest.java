package com.ouroboros.businessmodel.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.BusinessModelMetaCenter;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessModelExportHandlerTest {
  private static final String MODEL = "exportCases.Order";

  @Test
  void originalFiltersRelationsAndDisplayValuesProduceARealXlsxFile() throws Exception {
    var employee = mock(Employee.class); when(employee.getName()).thenReturn("张三");
    var organization = mock(OrganizationNode.class); when(organization.getName()).thenReturn("采购部");
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("owner");
    var dict = mock(DataDictItem.class); when(dict.getLabel()).thenReturn("已完成");
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("amount", 12); values.put("employee", employee); values.put("organization", organization);
    values.put("user", user); values.put("state", dict); values.put("date", new Date(0));
    values.put("localDate", LocalDate.of(2026, 10, 30)); values.put("time", LocalDateTime.of(2026, 10, 30, 10, 0));
    values.put("label", Map.of("label", "标签")); values.put("name", Map.of("name", "名称"));
    values.put("map", Map.of("code", "V")); values.put("empty", Map.of()); values.put("missing", null);
    values.put("list", List.of("a", 2)); values.put("text", "订单"); values.put("enabled", true);
    values.put("createdBy", "owner-id"); values.put("unknownCreatedBy", "missing-user");
    values.put("customer", Map.of("name", "客户"));
    var users = mock(UserCenter.class); when(users.getUser("owner-id")).thenReturn(Optional.of(user));
    var fields = values.keySet().stream().map(name -> Map.<String, Object>of("fieldName", name, "fieldLabel", name,
        "fieldType", name.endsWith("CreatedBy") || name.equals("createdBy") ? "CreatedBy" : "String",
        "value", name.equals("customer") ? "customer.name" : name)).toList();
    var meta = meta(fields); var data = mock(DataModel.class);
    when(data.query(anyMap())).thenReturn(Try.success(RecordList.of(List.of(values))));
    try (var metadata = mockStatic(BusinessModelMetaCenter.class); var models = mockStatic(DataModelCenter.class)) {
      metadata.when(() -> BusinessModelMetaCenter.getBusinessModelMeta(MODEL)).thenReturn(Optional.of(meta));
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(data));
      try (var file = new BusinessModelExportHandler(users).handle(context(), BusinessActionInput.paramsOnly(Map.of("query", Map.of("customer.id", "C1"))));
          var content = file.openStream(); var workbook = new XSSFWorkbook(content)) {
        assertEquals("订单.xlsx", file.fileName()); assertEquals(BusinessModelExportHandler.MEDIA_TYPE, file.mediaType());
        assertTrue(file.length() > 0); var sheet = workbook.getSheetAt(0);
        assertEquals(2, sheet.getPhysicalNumberOfRows()); assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
        assertEquals(12, sheet.getRow(1).getCell(0).getNumericCellValue());
        assertEquals("张三", sheet.getRow(1).getCell(1).getStringCellValue());
        assertEquals("采购部", sheet.getRow(1).getCell(2).getStringCellValue());
        assertEquals("owner", sheet.getRow(1).getCell(3).getStringCellValue());
        assertEquals("已完成", sheet.getRow(1).getCell(4).getStringCellValue());
        assertEquals("a, 2", sheet.getRow(1).getCell(13).getStringCellValue());
        assertEquals("owner", sheet.getRow(1).getCell(16).getStringCellValue());
        assertEquals("missing-user", sheet.getRow(1).getCell(17).getStringCellValue());
      }
      var query = ArgumentCaptor.forClass(Map.class); verify(data, times(1)).query(query.capture());
      assertEquals(Map.of("customer", Map.of("id", "C1")), query.getValue().get("where"));
      assertEquals(Map.of("customer", Map.of()), query.getValue().get("populate"));
    }
  }

  @Test
  void missingConfigurationDisabledFeatureAndFailedQueriesAreFailuresRatherThanEmptyFiles() {
    var meta = meta(List.of(Map.of("fieldName", "id", "fieldLabel", "ID")));
    var data = mock(DataModel.class); when(data.query(anyMap())).thenReturn(Try.failure(new IllegalStateException("query unavailable")));
    try (var metadata = mockStatic(BusinessModelMetaCenter.class); var models = mockStatic(DataModelCenter.class)) {
      metadata.when(() -> BusinessModelMetaCenter.getBusinessModelMeta(MODEL)).thenReturn(Optional.of(meta));
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(data));
      var handler = new BusinessModelExportHandler(mock(UserCenter.class));
      assertTrue(assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.empty())).getMessage().contains("查询导出"));
      assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.paramsOnly(Map.of("query", "invalid"))));
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.empty());
      assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.empty()));
      meta.setExtraProp("features", Map.of("isEnabledServerSideExport", true));
      assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.empty()));
      meta.setExtraProp("features", Map.of("isEnabledServerSideExport", true, "serverSideExport", Map.of("fields", List.of("bad", Map.of("fieldName", "")))));
      assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.empty()));
      meta.setExtraProp("features", Map.of("isEnabledServerSideExport", false));
      assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.empty()));
      metadata.when(() -> BusinessModelMetaCenter.getBusinessModelMeta(MODEL)).thenReturn(Optional.empty());
      assertThrows(BusinessException.class, () -> handler.handle(context(), BusinessActionInput.empty()));
      verify(data, times(1)).query(anyMap());
    }
  }

  @Test
  void failureAfterGenerationDeletesTheTemporaryFile() throws Exception {
    var meta = meta(List.of(Map.of("fieldName", "id", "fieldLabel", "ID"))); meta.setTitle("invalid\r\nname");
    var data = mock(DataModel.class); when(data.query(anyMap())).thenReturn(Try.success(RecordList.of(List.of(Map.of("id", "1")))));
    Set<Path> before = exportFiles();
    try (var metadata = mockStatic(BusinessModelMetaCenter.class); var models = mockStatic(DataModelCenter.class)) {
      metadata.when(() -> BusinessModelMetaCenter.getBusinessModelMeta(MODEL)).thenReturn(Optional.of(meta));
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(data));
      assertThrows(BusinessException.class, () -> new BusinessModelExportHandler(mock(UserCenter.class)).handle(context(), BusinessActionInput.empty()));
    }
    assertEquals(before, exportFiles());
  }

  private static Set<Path> exportFiles() throws Exception {
    try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return files.filter(path -> path.getFileName().toString().startsWith("ouroboros-model-export-")).collect(Collectors.toSet());
    }
  }

  private static DefaultBusinessModelMeta meta(List<?> fields) {
    var meta = new DefaultBusinessModelMeta(); meta.setNamespace("exportCases"); meta.setName("Order"); meta.setTitle("订单");
    meta.setExtraProp("features", Map.of("isEnabledServerSideExport", true, "serverSideExport", Map.of("fields", fields)));
    return meta;
  }

  private static BusinessActionContext context() {
    return new BusinessActionContext(MODEL, "export", MODEL + ".serverSideExport", "execution", null, Map.of(), null, null, true);
  }
}
