package com.ouroboros.businessmodel.event;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelMetaCenter;
import com.ouroboros.businessmodel.BusinessModelMetaParser;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.*;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.controller.DefaultBusinessModelServerSideExportController;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.FileResult;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real generated contract, Action transaction, source DataModel query, Excel and both HTTP entry points. */
class BusinessModelExportIntegrationTest {
  private static final String MODEL = "exportJourney.Order";
  private static final String AUTHORITY = MODEL + ".serverSideExport";

  @Test
  void existingExportGrantJavaAndBothHttpUrlsUseOneReadOnlyActionAndOneQueryPerCall() throws Exception {
    var meta = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse("""
        {"modelType":"default","namespace":"exportJourney","name":"Order","title":"订单","isEnabled":true,
          "fields":[{"name":"id","type":"String","isPrimaryKey":true},{"name":"state","type":"String"}],
          "features":{"isEnabledServerSideExport":true,"serverSideExport":{"fields":[
            {"fieldName":"id","fieldLabel":"编号","value":"id","fieldType":"String"},
            {"fieldName":"state","fieldLabel":"状态","value":"state","fieldType":"String"}]}}}
        """).get();
    var provider = new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
    var business = new DefaultBusinessModel(meta, provider);
    var dataMeta = business.buildModelBundle().getModel(DataModelMeta.class, MODEL).orElseThrow();
    dataMeta.setRawName("export_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    javax.sql.DataSource source; String dialect;
    String mysql = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
    if (mysql == null) {
      var h2 = new JdbcDataSource(); h2.setURL("jdbc:h2:mem:export_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE"); h2.setUser("sa");
      source = h2; dialect = "H2";
    } else {
      source = new DriverManagerDataSource(mysql, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
      dialect = "MySQL";
    }
    var transactions = new DataSourceTransactionManager(source);
    var station = new SqlDataStation("export", source, List.of(dataMeta), MigrationStrategy.AUTO, dialect);
    var jdbc = new JdbcTemplate(source);
    station.generateMigrationSql(station.getDataModelList()).getOrElseThrow(error -> error).forEach(jdbc::execute);
    var data = spy(station.getDataModelList().getFirst());
    var behaviors = new java.util.concurrent.atomic.AtomicReference<>(business.getBehavior());
    var contracts = new com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider() {
      public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.of(behaviors.get()); }
      public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, behaviors.get()); }
    };
    List<BusinessActionExecutionAuditRecord> audits = new ArrayList<>();
    var handler = new BusinessModelExportHandler(mock(UserCenter.class));
    var runtime = new DefaultBusinessActionRuntime(contracts, (flow, inputs) -> Try.failure(new AssertionError("export must use its Handler")),
        List.of(handler), transactions, audits::add);
    var mvc = MockMvcBuilders.standaloneSetup(new DefaultBusinessModelServerSideExportController(runtime),
        new BusinessActionDescriptionController(runtime)).build();
    try (var metadata = mockStatic(BusinessModelMetaCenter.class); var models = mockStatic(DataModelCenter.class)) {
      metadata.when(() -> BusinessModelMetaCenter.getBusinessModelMeta(MODEL)).thenReturn(Optional.of(meta));
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(data));
      models.when(DataModelCenter::getDataModelMap).thenReturn(Map.of(MODEL, data));
      models.when(DataModelCenter::hasSnapshot).thenReturn(true);
      data.batchInsert(List.of(Map.of("id", "1", "state", "DRAFT"), Map.of("id", "2", "state", "DONE"))).get();
      var input = BusinessActionInput.paramsOnly(Map.of("query", Map.of("state", "DONE")));
      assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".export"), () -> runtime.invoke(MODEL, "export", input)));
      try (var file = FileResult.class.cast(withPermissions(List.of(AUTHORITY), () -> runtime.invoke(MODEL, "export", input)));
          var content = file.openStream()) { assertWorkbook(content.readAllBytes()); }
      var oldHttp = withPermissions(List.of(AUTHORITY), () -> assertDoesNotThrow(() -> mvc.perform(
          get("/api/default-business-model-data-export/" + MODEL + "/export-excel").param("state", "DONE"))
          .andExpect(status().isOk()).andExpect(content().contentType(BusinessModelExportHandler.MEDIA_TYPE))
          .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")))
          .andReturn().getResponse()));
      assertWorkbook(oldHttp.getContentAsByteArray());
      var unified = withPermissions(List.of(AUTHORITY), () -> assertDoesNotThrow(() -> mvc.perform(
          post("/api/business-model-behaviors/" + MODEL + "/actions/export/invoke").contentType(MediaType.APPLICATION_JSON)
              .content("{\"input\":{\"params\":{\"query\":{\"state\":\"DONE\"}}}}"))
          .andExpect(status().isOk()).andExpect(content().contentType(BusinessModelExportHandler.MEDIA_TYPE)).andReturn().getResponse()));
      assertWorkbook(unified.getContentAsByteArray());
      verify(data, times(3)).query(anyMap());
      assertEquals(3, audits.stream().filter(audit -> "COMMITTED".equals(audit.transactionResult())).count());
      assertTrue(audits.stream().allMatch(audit -> AUTHORITY.equals(audit.executionAuthorityName())));
      assertEquals(2, data.count(Map.of()).get());
      meta.setExtraProp("features", Map.of("isEnabledServerSideExport", false));
      behaviors.set(new DefaultBusinessModel(meta, provider).getBehavior());
      assertThrows(BusinessException.class, () -> withPermissions(List.of(AUTHORITY), () -> runtime.invoke(MODEL, "export", input)));
      withPermissions(List.of(AUTHORITY), () -> assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(
          get("/api/default-business-model-data-export/" + MODEL + "/export-excel").param("state", "DONE"))));
      withPermissions(List.of(AUTHORITY), () -> assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(
          post("/api/business-model-behaviors/" + MODEL + "/actions/export/invoke").contentType(MediaType.APPLICATION_JSON)
              .content("{\"input\":{\"params\":{\"query\":{\"state\":\"DONE\"}}}}"))));
      verify(data, times(3)).query(anyMap());
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
  }

  private static void assertWorkbook(byte[] bytes) throws Exception {
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0); assertEquals(2, sheet.getPhysicalNumberOfRows());
      assertEquals("编号", sheet.getRow(0).getCell(0).getStringCellValue());
      assertEquals("2", sheet.getRow(1).getCell(0).getStringCellValue());
      assertEquals("DONE", sheet.getRow(1).getCell(1).getStringCellValue());
    }
  }
}
