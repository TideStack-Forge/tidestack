package com.ouroboros.devhelper.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.ouroboros.fieldtype.*;
import com.ouroboros.workflow.approval.CandidateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class EditorCatalogCommandsTest {
  @Test void fieldCatalogUsesTheApplicationsRuntimeScene() {
    var factory = new DefaultListableBeanFactory(); var catalog = mock(FieldTypeCatalog.class);
    var field = new FieldTypeDefinition(); field.setName("ApplicationField"); field.setLabel("应用字段");
    when(catalog.listMetadata(any())).thenAnswer(call -> {
      assertEquals(FieldTypeCatalogScene.RUNTIME, ((FieldTypeCatalogContext)call.getArgument(0)).getScene()); return List.of(field);
    });
    factory.registerSingleton("fields", catalog);
    var command = new EditorCatalogCommand(factory.getBeanProvider(FieldTypeCatalog.class));
    var result = command.execute(request(Map.of("kind", "field-types"))).join();
    assertTrue(result.getSuccess()); assertEquals(List.of(field), result.getData());
    assertFalse(command.execute(request(Map.of("kind", "unknown"))).join().getSuccess());
  }
  @Test void approvalCatalogQueriesApplicationProvidersForSearchRecallAndCategories() {
    var factory = new DefaultListableBeanFactory();
    var provider = mock(CandidateProvider.class);
    when(provider.getName()).thenReturn("ApplicationUsers"); when(provider.getLabel()).thenReturn("应用用户");
    when(provider.getConfigMode()).thenReturn(CandidateProvider.ConfigMode.Pick);
    when(provider.getValueKey()).thenReturn("users");
    when(provider.getCategoryLabel()).thenReturn("部门");
    var item = new CandidateProvider.CandidateItem(); item.setValue("user-1"); item.setLabel("Alice");
    when(provider.getItems()).thenReturn(List.of(item));
    when(provider.search("department", "Ali")).thenReturn(List.of(item));
    when(provider.search(List.of("user-1"))).thenReturn(List.of(item));
    var category = new CandidateProvider.CandidateCategory(); category.setValue("department"); category.setLabel("部门");
    when(provider.getCategories()).thenReturn(List.of(category));
    factory.registerSingleton("candidates", provider);
    var command = new ApprovalCandidatesCommand(factory.getBeanProvider(CandidateProvider.class));
    var types = command.execute(request(Map.of("operation", "types"))).join();
    assertTrue(types.getSuccess(), types.getMessage());
    var descriptors = com.ouroboros.util.Json.toBean(com.fasterxml.jackson.databind.JsonNode.class,
        com.ouroboros.util.Json.toJsonString(types.getData()));
    assertEquals(1, descriptors.size());
    assertEquals("ApplicationUsers", descriptors.get(0).path("name").asText());
    assertEquals("应用用户", descriptors.get(0).path("label").asText());
    assertEquals("Pick", descriptors.get(0).path("configMode").asText());
    assertEquals("users", descriptors.get(0).path("valueKey").asText());
    assertEquals("部门", descriptors.get(0).path("categoryLabel").asText());
    assertEquals("user-1", descriptors.get(0).path("items").get(0).path("value").asText());
    assertEquals(10, descriptors.get(0).size());
    assertEquals(List.of(item), command.execute(request(Map.of("operation", "items", "type", "ApplicationUsers", "category", "department", "keyword", "Ali"))).join().getData());
    assertEquals(List.of(item), command.execute(request(Map.of("operation", "items", "type", "ApplicationUsers", "values", List.of("user-1")))).join().getData());
    assertEquals(List.of(category), command.execute(request(Map.of("operation", "category", "type", "ApplicationUsers"))).join().getData());
    assertFalse(command.execute(request(Map.of("operation", "items", "type", "unknown"))).join().getSuccess());
    assertFalse(command.execute(request(Map.of("operation", "unknown", "type", "ApplicationUsers"))).join().getSuccess());
  }
  private CommandRequest request(Map<String, Object> params) { return new CommandRequest().setSequenceId(UUID.randomUUID().toString()).setParams(params); }
}
