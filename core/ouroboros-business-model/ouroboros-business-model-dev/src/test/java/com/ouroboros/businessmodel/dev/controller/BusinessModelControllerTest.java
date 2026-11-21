package com.ouroboros.businessmodel.dev.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.dev.service.BusinessModelModuleTreeService;
import com.ouroboros.businessmodel.dev.service.BusinessModelModuleTreeService.ModuleTreeNodeVO;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;

class BusinessModelControllerTest {
  @Test
  void newModelInitializationUsesTheCurrentFormatAndCanReferenceAGeneratedAction() throws Exception {
    var controller = new BusinessModelController();
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/api/dev/example/business-model/init-form")).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    var envelope = com.ouroboros.util.Json.toMap(response.getContentAsString());
    var data = (Map<?, ?>) envelope.get("data");
    assertEquals("2.0", data.get("formatVersion"));
    var meta = (com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta)
        com.ouroboros.businessmodel.BusinessModelMetaParser.parse(com.ouroboros.util.Json.toJsonString(data)).get();
    assertTrue(com.ouroboros.businessmodel.BusinessModelMetaParser.isCurrent(meta));
    meta.setNamespace("newFormat"); meta.setName("Record");
    var id = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta();
    id.setName("id"); id.setType("单行文本"); id.setIsPrimaryKey(true); meta.setFields(List.of(id));
    meta.setExtraProp("custom", List.of(Map.of("name", "lookup", "api", Map.of("isEnabled", true,
        "implementationMode", "ACTION", "action", "search"))));
    var model = new com.ouroboros.businessmodel.DefaultBusinessModel(meta, org.mockito.Mockito.mock(com.ouroboros.businessmodel.BusinessModelProvider.class));
    assertEquals(1, model.getActions().stream().filter(action -> "search".equals(action.getName())).count());
    assertTrue(model.getActions().stream().noneMatch(action -> "lookup".equals(action.getName())));
    assertTrue(model.getCustomConfigs().getFirst().getApi().isActionReference());
    assertEquals("search", model.getCustomConfigs().getFirst().getApi().getActionName("lookup"));
    assertTrue(model.getCustomConfigs().getFirst().getApi().definitionErrors().isEmpty());
  }

  @Test
  void fullDraftPreviewUsesTheEstablishedHttpEnvelopeAndEchoesItsRequestIdentity() throws Exception {
    var controller = new BusinessModelController();
    controller.businessModelDevService = new BusinessModelDevService();
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/dev/example/business-model/behavior-preview").param("requestId", "draft-7")
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{"))
        .andReturn().getResponse();
    assertEquals(200, response.getStatus());
    var envelope = com.ouroboros.util.Json.toMap(response.getContentAsString());
    var preview = (Map<?, ?>) envelope.get("data");
    assertEquals("draft-7", preview.get("requestId"));
    assertEquals("draft", preview.get("view"));
    assertEquals(null, preview.get("catalog"));
    assertEquals("INVALID", ((Map<?, ?>) preview.get("validationSnapshot")).get("validationState"));
  }

  @Test
  void modelBundleExposesBehaviorCandidatesThroughTheEstablishedHttpEnvelope() throws Exception {
    var action = new com.ouroboros.businessmodel.behavior.ActionDefinition();
    action.setName("confirm"); action.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of("type", "object"))));
    var event = new com.ouroboros.businessmodel.behavior.EventDefinition();
    event.setName("paid"); event.setSubject("model"); event.setPayloadSchema(Map.of("type", "object"));
    var bundle = new ModelBundle();
    bundle.putModel(com.ouroboros.businessmodel.behavior.BusinessModelBehavior.class, "sales.Order",
        new com.ouroboros.businessmodel.behavior.BusinessModelBehavior("sales.Order", List.of(action), List.of(event), List.of()));
    var controller = new BusinessModelController();
    controller.businessModelDevService = new StubBusinessModelDevService(bundle);
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/api/dev/example/business-model/sales.Order/model-bundle")).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    var envelope = com.ouroboros.util.Json.toMap(response.getContentAsString());
    var data = (Map<?, ?>) envelope.get("data");
    var entry = (Map<?, ?>) ((List<?>) data.get("BusinessModelBehavior")).getFirst();
    assertEquals("sales.Order", entry.get("name"));
    var behavior = (Map<?, ?>) entry.get("model");
    var events = (Map<?, ?>) behavior.get("events");
    assertEquals("model", ((Map<?, ?>) events.get("paid")).get("subject"));
    var actions = (Map<?, ?>) behavior.get("actions");
    assertTrue(((Map<?, ?>) actions.get("confirm")).containsKey("inputSchema"));
  }

  @Test
  void typeExportUsesTheExistingModelBundleInsteadOfASeparateRuntimeCatalog() {
    var controller = new BusinessModelController();
    var bundle = new ModelBundle();
    bundle.putModel(com.ouroboros.businessmodel.behavior.BusinessModelBehavior.class, "sales.Order",
        new com.ouroboros.businessmodel.behavior.BusinessModelBehavior("sales.Order", List.of(), List.of(), List.of()));
    controller.businessModelDevService = new StubBusinessModelDevService(bundle);
    var result = controller.getTypes("sales.Order", "example.contract", "OrderBusiness");
    assertEquals(List.of("example/contract/OrderBusiness.java"), List.copyOf(result.getData().javaSources().keySet()));
    assertTrue(result.getData().typeScript().length() > 0);
  }


  @Test
  void projectionTargetChecksPersistedTargetAndReturnsCanonicalEditUrl() throws Exception {
    var controller = new BusinessModelController();
    var bundle = new ModelBundle();
    bundle.putModel(SchemaNode.class, "sales/order/add", new SchemaNode());
    controller.businessModelDevService = new StubBusinessModelDevService(bundle);
    var exists = new java.util.concurrent.atomic.AtomicBoolean(false);
    controller.uiSchemaDevService = new StubDevelopService<SchemaNodeWrapper>() {
      @Override
      public Optional<SchemaNodeWrapper> getOne(String name) {
        assertEquals("sales.order.add", name);
        return exists.get() ? Optional.of(new SchemaNodeWrapper()) : Optional.empty();
      }
    };
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    for (boolean present : List.of(false, true)) {
      exists.set(present);
      var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
          .get("/api/dev/crm/business-model/sales.Order/projection-target")
          .param("kind", "ui-schema").param("name", "sales/order/add"))
          .andReturn().getResponse();
      assertEquals(200, response.getStatus());
      var data = (Map<?, ?>) com.ouroboros.util.Json.toMap(response.getContentAsString()).get("data");
      assertEquals(present, data.get("isCloned"));
      assertEquals("/dev/crm/ui-schema/sales.order.add", data.get("editUrl"));
    }
  }

  @Test
  void cloneReusesExistingTargetsWithoutOverwritingUserEdits() {
    var controller = new BusinessModelController();
    controller.logicModelDevService = new StubDevelopService<FlowModelMeta>() {
      @Override public Optional<FlowModelMeta> getOne(String name) {
        return Optional.of(FlowModelMeta.builder().name(name).build());
      }
      @Override public Try<FlowModelMeta> create(FlowModelMeta model, String message) {
        throw new AssertionError("Must not overwrite existing logicflow");
      }
    };
    controller.uiSchemaDevService = new StubDevelopService<SchemaNodeWrapper>() {
      @Override public Optional<SchemaNodeWrapper> getOne(String name) { return Optional.of(new SchemaNodeWrapper()); }
      @Override public Try<SchemaNodeWrapper> create(SchemaNodeWrapper model, String message) {
        throw new AssertionError("Must not overwrite existing schema");
      }
    };
    assertEquals("sales.Order.add", controller.cloneFlowModel("sales.Order", "sales.Order.add").getData());
    assertEquals("sales.order.add", controller.cloneUiSchema("sales.Order", "sales/order/add").getData());
  }

  @Test
  void getProjectModel_marksClonedFlowAndSchemaModels() {
    var controller = new BusinessModelController();
    var bundle = new ModelBundle();
    bundle.putModel(FlowModelMeta.class, "order-flow", FlowModelMeta.builder().name("order-flow").build());
    var schemaNode = new SchemaNode();
    schemaNode.setType("page");
    bundle.putModel(SchemaNode.class, "order/form/detail", schemaNode);

    controller.businessModelDevService = new StubBusinessModelDevService(bundle);
    controller.moduleTreeService = new StubBusinessModelModuleTreeService();
    controller.logicModelDevService = new StubDevelopService<FlowModelMeta>() {
      @Override
      public Optional<FlowModelMeta> getOne(String fullName) {
        return "order-flow".equals(fullName) ? Optional.of(FlowModelMeta.builder().name(fullName).build()) : Optional.empty();
      }
    };
    controller.uiSchemaDevService = new StubDevelopService<SchemaNodeWrapper>() {
      @Override
      public Optional<SchemaNodeWrapper> getOne(String fullName) {
        return "order.form.detail".equals(fullName) ? Optional.of(new SchemaNodeWrapper()) : Optional.empty();
      }
    };

    var result = controller.getProjectModel("crm", "crm.order");
    var response = result.getData();

    var flowModels = castModelList(response.get("FlowModelMeta"));
    assertEquals(true, flowModels.get(0).get("isCloned"));
    assertEquals("/dev/crm/logicflow-model/order-flow", flowModels.get(0).get("editUrl"));

    var schemaModels = castModelList(response.get("SchemaNode"));
    assertEquals(true, schemaModels.get(0).get("isCloned"));
    assertEquals("order.form.detail", schemaModels.get(0).get("editName"));
    assertEquals("/dev/crm/ui-schema/order.form.detail", schemaModels.get(0).get("editUrl"));
    assertTrue(schemaModels.get(0).containsKey("name"));
  }

  @Test
  void getBusinessModelGraph_returnsAllModelsAndTheirRelations() {
    var customer = new DefaultBusinessModelMeta();
    customer.setName("customer");
    customer.setNamespace("crm");
    customer.setTitle("客户");

    var order = new DefaultBusinessModelMeta();
    order.setName("order");
    order.setNamespace("crm");
    order.setTitle("订单");
    var relation = new BusinessModelRelationMeta();
    relation.setName("customer");
    relation.setTitle("所属客户");
    relation.setModel(customer.getFullName());
    relation.setType("manyToOne");
    order.setRelations(Arrays.asList(relation));

    var controller = new BusinessModelController();
    controller.businessModelDevService = new StubBusinessModelDevService(null) {
      @Override
      public List<com.ouroboros.businessmodel.meta.BusinessModelMeta> getAll() {
        return Arrays.asList(order, customer);
      }
      @Override
      protected com.ouroboros.vcs.file.VCSFile getExistingOrPrimaryModelVCSFile(String fullName) {
        return org.mockito.Mockito.mock(com.ouroboros.vcs.file.VCSFile.class);
      }
    };

    var graph = controller.getBusinessModelGraph("crm").getData();

    assertEquals(2, graph.getNodes().size());
    assertEquals("crm.order", graph.getNodes().get(0).getFullName());
    assertEquals(1, graph.getEdges().size());
    assertEquals("crm.order", graph.getEdges().get(0).getSource());
    assertEquals("crm.customer", graph.getEdges().get(0).getTarget());
    assertEquals("master", graph.getEdges().get(0).getType());
  }

  private static List<Map<String, Object>> castModelList(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private static class StubBusinessModelDevService extends BusinessModelDevService {
    private final ModelBundle bundle;

    private StubBusinessModelDevService(ModelBundle bundle) {
      this.bundle = bundle;
    }

    @Override
    public Optional<com.ouroboros.businessmodel.BusinessModel> getBusinessModel(String fullName) {
      return Optional.empty();
    }

    @Override
    public Map<String, com.ouroboros.businessmodel.BusinessModel> getBusinessModelMap() {
      return Collections.emptyMap();
    }

    @Override
    public Optional<ModelBundle> getModelBundle(String fullName) {
      return Optional.of(bundle);
    }
  }

  private static class StubBusinessModelModuleTreeService implements BusinessModelModuleTreeService {
    @Override
    public Try<List<ModuleTreeNodeVO>> getModuleTree() {
      return Try.success(Collections.emptyList());
    }

    @Override
    public Try<Void> addModule(ModuleTreeNodeVO node) {
      return Try.success(null);
    }

    @Override
    public Try<Void> updateModule(String fullName, ModuleTreeNodeVO node) {
      return Try.success(null);
    }

    @Override
    public Try<Void> deleteModule(String fullName) {
      return Try.success(null);
    }
  }

  private abstract static class StubDevelopService<M> implements DevelopService<M> {
    @Override
    public Try<Void> lock(String fullName, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> lock(String fullName, boolean force, M oldModel, M newModel) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(String fullName, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(String fullNames, boolean force, M oldModel, M newModel) {
      return Try.success(null);
    }

    @Override
    public Try<M> create(M m, String commitMsg) {
      return Try.success(m);
    }

    @Override
    public Try<M> delete(String fullName, String commitMsg) {
      return Try.success(null);
    }

    @Override
    public Try<M> update(String fullName, M m) {
      return Try.success(m);
    }

    @Override
    public Try<Boolean> rollback(String fullName) {
      return Try.success(Boolean.TRUE);
    }

    @Override
    public Try<Boolean> commit(String fullName, String commitMsg) {
      return Try.success(Boolean.TRUE);
    }

    @Override
    public Optional<M> getOne(String fullName) {
      return Optional.empty();
    }

    @Override
    public Try<?> getInitFormData(String fullName) {
      return Try.success(null);
    }

    @Override
    public Try<?> getStatus(String fullName) {
      return Try.success(null);
    }

    @Override
    public List<?> search(Map<String, Object> searchParam) {
      return Collections.emptyList();
    }

    @Override
    public List<com.ouroboros.vcs.versioning.ChangeLog> getAllChangeLogs(String fullName) {
      return Collections.emptyList();
    }

    @Override
    public List<com.ouroboros.vcs.versioning.ChangeLog> getChangeLogs(String fullName, int page, int size) {
      return Collections.emptyList();
    }

    @Override
    public Try<M> buildModelFromFileContent(String fileContent) {
      return Try.success(null);
    }

    @Override
    public Try<M> buildModelFromRequestBody(String requestBody) {
      return Try.success(null);
    }
  }
}
