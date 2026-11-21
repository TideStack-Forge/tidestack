package com.ouroboros.organizationstructure.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.service.StructureManageService;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminLevelServiceImplTest {

  @Mock
  private StructureManageService structureManageService;

  @Test
  void updateShouldRejectCodeMutation() throws Exception {
    AdminLevelServiceImpl service = new AdminLevelServiceImpl();
    injectDataModel(service, dataModel(
        adminLevel("1", "DEPARTMENT", "部门", "DEPT", 1, true)));
    injectStructureManageService(service, Collections.emptyList());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.update("1", adminLevel(null, "DEPARTMENT-NEW", "部门", "DEPT", 1, true)));
    assertEquals("行政级别代码不可修改", exception.getMessage());
  }

  @Test
  void createShouldRejectDuplicateRankWithinSameScope() throws Exception {
    AdminLevelServiceImpl service = new AdminLevelServiceImpl();
    injectDataModel(service, dataModel(
        adminLevel("1", "DEPARTMENT", "部门", "DEPT", 1, true),
        adminLevel("2", "DIVISION", "处室", "DEPT", 2, true)));
    injectStructureManageService(service, Collections.emptyList());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.create(adminLevel(null, "SECTION", "科室", "DEPT", 2, true)));
    assertEquals("同范围内排序 2 已被 处室 占用", exception.getMessage());
  }

  @Test
  void findEnabledByScopeShouldFilterDisabledLevels() throws Exception {
    AdminLevelServiceImpl service = new AdminLevelServiceImpl();
    injectDataModel(service, dataModel(
        adminLevel("1", "DEPARTMENT", "部门", "DEPT", 1, true),
        adminLevel("2", "DIVISION", "处室", "DEPT", 2, false),
        adminLevel("3", "GROUP", "集团", "ORG", 1, true)));
    injectStructureManageService(service, Collections.emptyList());

    List<AdminLevelEntity> items = service.findEnabledByScope("DEPT");
    assertEquals(1, items.size());
    assertEquals("DEPARTMENT", items.get(0).getCode());
  }

  @Test
  void deleteShouldRejectReferencedLevel() throws Exception {
    AdminLevelServiceImpl service = new AdminLevelServiceImpl();
    injectDataModel(service, dataModel(
        adminLevel("1", "DEPARTMENT", "部门", "DEPT", 1, true)));
    injectStructureManageService(service, Collections.singletonList(node("node-1", "DEPARTMENT")));

    BusinessException exception = assertThrows(BusinessException.class, () -> service.delete("1"));
    assertEquals("行政级别已被组织节点引用，无法删除", exception.getMessage());
  }

  @Test
  void validateAdminLevelReferenceShouldRejectScopeMismatch() throws Exception {
    AdminLevelServiceImpl service = new AdminLevelServiceImpl();
    injectDataModel(service, dataModel(
        adminLevel("1", "DEPARTMENT", "部门", "DEPT", 1, true)));
    injectStructureManageService(service, Collections.emptyList());

    BusinessException exception = assertThrows(BusinessException.class,
        () -> service.validateAdminLevelReference("DEPARTMENT", "company"));

    assertEquals("组织节点不能引用部门级行政级别", exception.getMessage());
  }

  @Test
  void verifyExistingOrganizationNodeAdminLevelsShouldReportMissingAndDisabledReferences() throws Exception {
    AdminLevelServiceImpl service = new AdminLevelServiceImpl();
    injectDataModel(service, dataModel(
        adminLevel("1", "DEPARTMENT", "部门", "DEPT", 1, true),
        adminLevel("2", "GROUP", "集团", "ORG", 1, false)));
    injectStructureManageService(service,
        Arrays.asList(node("node-1", "DEPARTMENT"), node("node-2", "GROUP"), node("node-3", "MISSING")));

    List<String> warnings = service.verifyExistingOrganizationNodeAdminLevels();
    assertEquals(2, warnings.size());
  }

  private AdminLevelEntity adminLevel(String id, String code, String name, String scope, Integer rank, Boolean enabled) {
    AdminLevelEntity entity = new AdminLevelEntity();
    entity.setId(id);
    entity.setCode(code);
    entity.setName(name);
    entity.setScope(scope);
    entity.setRank(rank);
    entity.setEnabled(enabled);
    return entity;
  }

  private OrganizationNodeEntity node(String id, String adminLevel) {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setId(id);
    entity.setAdminLevel(adminLevel);
    return entity;
  }

  private TypedDataModel<String, AdminLevelEntity> dataModel(AdminLevelEntity... entities) {
    TypedDataModel<String, AdminLevelEntity> dataModel = mock(TypedDataModel.class);
    when(dataModel.query(anyMap())).thenReturn(Try.success(Arrays.asList(entities)));
    when(dataModel.update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(AdminLevelEntity.class))).thenReturn(Try.success(1L));
    when(dataModel.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(Try.success(1L));
    when(dataModel.insert(org.mockito.ArgumentMatchers.any(AdminLevelEntity.class))).thenAnswer(invocation -> Try.success((AdminLevelEntity) invocation.getArguments()[0]));
    return dataModel;
  }

  private void injectStructureManageService(AdminLevelServiceImpl service, java.util.List<OrganizationNodeEntity> nodes) throws Exception {
    java.lang.reflect.Field field = AdminLevelServiceImpl.class.getDeclaredField("structureManageService");
    field.setAccessible(true);
    when(structureManageService.findAll()).thenReturn(nodes);
    field.set(service, structureManageService);
  }

  private void injectDataModel(AdminLevelServiceImpl service, TypedDataModel<String, AdminLevelEntity> dataModel) throws Exception {
    java.lang.reflect.Field field = AdminLevelServiceImpl.class.getDeclaredField("dataModel");
    field.setAccessible(true);
    field.set(service, dataModel);
  }
}
