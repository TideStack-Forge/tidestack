package com.ouroboros.statistics.reporting.dev.controller;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ouroboros.businessmodel.*;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.datasource.model.DataSourceDefine;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.service.*;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.runtime.execution.*;
import com.ouroboros.statistics.reporting.runtime.executor.*;
import com.ouroboros.util.Json;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompositeViewDesignControllerTest {
  private AnnotationConfigApplicationContext context;
  private CompositeViewDesignController controller;
  private CompositeViewBusinessModelMeta view;

  @BeforeEach
  void setup() {
    var definition = Json.toBean(LowcodeDatasetDefinition.class, """
        {"name":"demo.orders","isEnabled":true,
         "queryDefinition":{"type":"sql","sql":"select 1 as id union all select 2"},
         "schema":{"fields":[{"name":"id","title":"编号","dataType":"integer","semanticRole":"dimension","nullable":false,"sortable":true,"filterable":true}]}}
        """);
    var datasets = mock(DatasetDevService.class);
    when(datasets.getOne("demo.orders")).thenReturn(Optional.of(definition));
    doReturn(List.of(definition)).when(datasets).search(anyMap());
    var models = new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
    context = new AnnotationConfigApplicationContext();
    context.register(SpringContextUtil.class);
    context.getBeanFactory().registerSingleton("workspaceDatasets", datasets);
    context.getBeanFactory().registerSingleton("workspaceModels", models);
    context.refresh();
    DevAppHolder.setAppName("demo");
    var sources = mock(DatasetDesignDataSourceService.class);
    when(sources.connection(eq("demo"), any())).thenReturn(Optional.of(new DataSourceDefine(Map.of(
        "type", "jdbc", "driverClassName", "org.h2.Driver", "url", "jdbc:h2:mem:composite_design_test", "username", "sa", "password", ""))));
    var dialects = new DefaultSqlDialectResolver();
    var backend = new JdbcSqlExecutionBackend();
    var factory = new DatasetPreviewExecutorFactory(sources, dialects, backend);
    var config = mock(DatasetDevConfigService.class, CALLS_REAL_METHODS);
    controller = new CompositeViewDesignController(datasets, config, context.getBeanProvider(BusinessModelProvider.class),
        factory, List.of(new SqlDatasetExecutor(dialects, (query, request) -> null, backend)),
        request -> Map.of("currentUser", "server-account"), new DefaultDatasetParameterNormalizer(), new DefaultDatasetPermissionInjector());
    view = Json.toBean(CompositeViewBusinessModelMeta.class, """
        {"modelType":"composite-view","namespace":"demo","name":"Orders","isEnabled":true,
         "datasetName":"demo.orders","columns":[{"field":"id","title":"编号"}],
         "layoutConfig":{"pageSize":1,"defaultSort":[{"field":"id","direction":"asc"}]}}
        """);
  }

  @AfterEach
  void cleanup() { DevAppHolder.removeAppName(); context.close(); }

  @Test
  void exposesContractsAndDiagnosticsAsObjectResponsesAndExecutesRealJdbcPreview() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/api/dev/demo/composite-view/contracts"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.datasets[0].name").value("demo.orders"));
    mvc.perform(get("/api/dev/demo/composite-view/execution-policy"))
        .andExpect(jsonPath("$.data.maxPageSize").value(1000));
    mvc.perform(post("/api/dev/demo/composite-view/validate").contentType("application/json").content(Json.toJsonString(view)))
        .andExpect(jsonPath("$.data.diagnostics").isEmpty());
    var request = new DatasetPreviewRequest();
    var page = new PageSpec(); page.setPageNo(2); page.setPageSize(1); request.setPage(page);
    mvc.perform(post("/api/dev/demo/composite-view/preview").contentType("application/json")
        .content(Json.toJsonString(new CompositeViewDesignController.Preview(view, request))))
        .andExpect(jsonPath("$.data.rows[0].id").value(2)).andExpect(jsonPath("$.data.pageInfo.total").value(2));
  }

  @Test
  void returnsDraftDiagnosticsAndBlocksPreviewUntilReferencesAreRepaired() {
    view.getColumns().getFirst().setField("missing");
    var errors = controller.validate(view).getData().get("diagnostics");
    assertTrue(errors.stream().anyMatch(message -> message.contains("columns[0].field")));
    assertThrows(BusinessException.class, () -> controller.preview("demo", new CompositeViewDesignController.Preview(view, null)));
    view.getColumns().getFirst().setField("id");
    assertEquals(1, controller.preview("demo", new CompositeViewDesignController.Preview(view, null)).getData().getRows().size());
  }

  @Test
  void executesTheOwnerPrivateSourceAndRejectsMissingTrustedContextDeclarations() {
    view.setDatasetName(null);
    view.setEmbeddedQuery(Map.of("queryDefinition", Map.of("type", "sql", "sql", "select 7 as id"),
        "schema", Map.of("fields", List.of(Map.of("name", "id", "dataType", "integer", "semanticRole", "dimension", "nullable", false, "sortable", true))),
        "requiredContext", List.of("currentUser")));
    assertTrue(controller.validate(view).getData().get("diagnostics").isEmpty());
    assertEquals(7, controller.preview("demo", new CompositeViewDesignController.Preview(view, null))
        .getData().getRows().getFirst().get("id"));
    view.setEmbeddedQuery(Map.of("queryDefinition", Map.of("type", "sql", "sql", "select 7 as id")));
    assertFalse(controller.validate(view).getData().get("diagnostics").isEmpty());
  }
}
