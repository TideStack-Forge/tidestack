package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.schema.CompositeViewRuntimeSchema;
import com.ouroboros.businessmodel.schema.CompositeViewWebApis;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeViewRuntimeSchemaTest {
  @Test
  void rejectsAmbiguousOperationNamesAndResolvesExplicitHttpOperations() {
    var first = SimpleWebAPIModel.builder().post("/api/contract/first").operationId("contract.shared").build();
    var second = SimpleWebAPIModel.builder().post("/api/contract/second").operationId("contract.shared").build();
    var bundle = new ModelBundle();
    bundle.putModel(WebAPIModel.class, first.getApi(), first);
    bundle.putModel(WebAPIModel.class, second.getApi(), second);
    var model = mock(BusinessModel.class);
    when(model.isEnabled()).thenReturn(true);
    when(model.compositeView()).thenReturn(java.util.Optional.empty());
    when(model.buildModelBundle()).thenReturn(bundle);
    var provider = mock(BusinessModelProvider.class);
    when(provider.getBusinessModelMap()).thenReturn(Map.of("contract.test", model));

    assertThrows(BusinessException.class, () -> CompositeViewWebApis.resolve(provider, "contract.shared"));
    assertThrows(BusinessException.class, () -> CompositeViewWebApis.resolve(provider, "contract.missing"));
    assertSame(second, CompositeViewWebApis.resolve(provider, second.getApi()));
  }

  @Test
  void projectsNestedBindingsAndPathVariablesUsingTheConfiguredColumnName() {
    var api = SimpleWebAPIModel.builder().post("/contract/orders/{orderId}/approve")
        .operationId("contract.test.approve").build();
    var bundle = new ModelBundle();
    bundle.putModel(WebAPIModel.class, api.getApi(), api);
    var businessModel = mock(BusinessModel.class);
    when(businessModel.isEnabled()).thenReturn(true);
    when(businessModel.compositeView()).thenReturn(java.util.Optional.empty());
    when(businessModel.buildModelBundle()).thenReturn(bundle);
    var provider = mock(BusinessModelProvider.class);
    when(provider.getBusinessModelMap()).thenReturn(Map.of("contract.test", businessModel));
    var view = new CompositeViewBusinessModelMeta();
    view.setName("contract.test.view");
    var column = new CompositeViewBusinessModelMeta.ColumnDef();
    column.setField("Id");
    column.setTitle("编号");
    view.setColumns(List.of(column));
    var action = new CompositeViewBusinessModelMeta.ActionButtonDef();
    action.setApiRef("contract.test.approve");
    action.setLabel("处理");
    action.setScope("row");
    action.setContextBinding(Map.of("orderId", "${id}", "payload.orderId", "ID"));
    view.setActionButtons(List.of(action));

    var schema = Json.mapperCopy().valueToTree(CompositeViewRuntimeSchema.build(view, provider));
    var projectedApi = schema.at("/body/columns/1/buttons/0/api");
    assertEquals("/api/contract/orders/${Id}/approve", projectedApi.get("url").asText());
    assertEquals("${Id}", projectedApi.at("/data/payload/orderId").asText());

    action.setScope("batch");
    schema = Json.mapperCopy().valueToTree(CompositeViewRuntimeSchema.build(view, provider));
    assertEquals("${selectedItems|pick:Id}", schema.at("/body/bulkActions/0/api/data/payload/orderId").asText());
  }
}
