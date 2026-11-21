package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonnelProfileFormSchemeServiceImplTest {

  @Test
  void findDefaultSchemeShouldReturnEnabledDefaultForRequestedApp() throws Exception {
    PersonnelProfileFormSchemeServiceImpl service = new PersonnelProfileFormSchemeServiceImpl();
    injectDataModel(service, dataModel(
        scheme("1", null, "全局方案", true, true, 0),
        scheme("2", "hr-app", "应用方案", true, true, 10),
        scheme("3", "hr-app", "应用备用方案", true, false, 100)));

    Optional<PersonnelProfileFormSchemeEntity> resolved = service.findDefaultScheme("hr-app");

    assertTrue(resolved.isPresent());
    assertEquals("2", resolved.get().getId());
  }

  @Test
  void findDefaultSchemeShouldReturnGlobalDefaultWhenAppNameIsBlank() throws Exception {
    PersonnelProfileFormSchemeServiceImpl service = new PersonnelProfileFormSchemeServiceImpl();
    injectDataModel(service, dataModel(
        scheme("1", null, "全局方案", true, true, 0),
        scheme("2", "hr-app", "应用方案", true, true, 10)));

    Optional<PersonnelProfileFormSchemeEntity> resolved = service.findDefaultScheme(null);

    assertTrue(resolved.isPresent());
    assertEquals("1", resolved.get().getId());
  }

  @Test
  void createShouldRejectDuplicateDefaultSchemeWithinSameApp() throws Exception {
    PersonnelProfileFormSchemeServiceImpl service = new PersonnelProfileFormSchemeServiceImpl();
    injectDataModel(service, dataModel(
        scheme("1", "hr-app", "默认方案", true, true, 0)));

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.create(scheme(null, "hr-app", "新的默认方案", true, true, 1), "operator"));

    assertEquals("同一应用下只能存在一个默认人员档案表单方案", exception.getMessage());
  }

  @Test
  void searchAllShouldFilterByAppName() throws Exception {
    PersonnelProfileFormSchemeServiceImpl service = new PersonnelProfileFormSchemeServiceImpl();
    injectDataModel(service, dataModel(
        scheme("1", "hr-app", "方案A", true, true, 0),
        scheme("2", "other-app", "方案B", true, true, 0),
        scheme("3", null, "全局方案", true, true, 0)));

    List<PersonnelProfileFormSchemeEntity> items = service.searchAll("hr-app");

    assertEquals(1, items.size());
    assertEquals("1", items.get(0).getId());
  }

  private PersonnelProfileFormSchemeEntity scheme(String id,
                                                  String appName,
                                                  String name,
                                                  boolean enabled,
                                                  boolean isDefault,
                                                  int priority) {
    PersonnelProfileFormSchemeEntity entity = new PersonnelProfileFormSchemeEntity();
    entity.setId(id);
    entity.setAppName(appName);
    entity.setName(name);
    entity.setEnabled(enabled);
    entity.setIsDefault(isDefault);
    entity.setPriority(priority);
    entity.setCreateSchema(pageSchema("create"));
    entity.setEditSchema(pageSchema("edit"));
    entity.setDetailSchema(pageSchema("detail"));
    return entity;
  }

  private java.util.Map<String, Object> pageSchema(String marker) {
    java.util.Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("type", "page");
    schema.put("marker", marker);
    schema.put("body", Collections.emptyList());
    return schema;
  }

  private TypedDataModel<String, PersonnelProfileFormSchemeEntity> dataModel(PersonnelProfileFormSchemeEntity... entities) {
    TypedDataModel<String, PersonnelProfileFormSchemeEntity> dataModel = mock(TypedDataModel.class);
    when(dataModel.query(anyMap())).thenReturn(Try.success(Arrays.asList(entities)));
    when(dataModel.insert(org.mockito.ArgumentMatchers.any(PersonnelProfileFormSchemeEntity.class)))
        .thenAnswer(invocation -> Try.success((PersonnelProfileFormSchemeEntity) invocation.getArguments()[0]));
    when(dataModel.update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(PersonnelProfileFormSchemeEntity.class)))
        .thenReturn(Try.success(1L));
    when(dataModel.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(Try.success(1L));
    return dataModel;
  }

  private void injectDataModel(PersonnelProfileFormSchemeServiceImpl service,
                               TypedDataModel<String, PersonnelProfileFormSchemeEntity> dataModel) throws Exception {
    java.lang.reflect.Field field = PersonnelProfileFormSchemeServiceImpl.class.getDeclaredField("dataModel");
    field.setAccessible(true);
    field.set(service, dataModel);

    java.lang.reflect.Field selfField = PersonnelProfileFormSchemeServiceImpl.class.getDeclaredField("self");
    selfField.setAccessible(true);
    selfField.set(service, service);
  }
}
