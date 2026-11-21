package com.ouroboros.organizationstructure.ui.provider;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import jakarta.annotation.Priority;

import com.ouroboros.organizationstructure.profile.service.PersonnelProfileRuntimeConfigService;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.uischema.UISchemaProvider;

@Priority(120)
public class EmployeePersonnelProfileUISchemaProvider implements UISchemaProvider {
  private static final String EDIT_EMPLOYEE_PATH = "/system-setting/organization-management/edit-employee";
  private static final String EMPLOYEE_DETAIL_PATH = "/system-setting/organization-management/employee-detail";

  @Override
  public Optional<Object> getUISchema(String path) {
    if (EDIT_EMPLOYEE_PATH.equals(path)) {
      return Optional.of(currentRuntimeConfigService().getResolvedEditSchema());
    }
    if (EMPLOYEE_DETAIL_PATH.equals(path)) {
      return Optional.of(currentRuntimeConfigService().getResolvedDetailSchema());
    }
    return Optional.empty();
  }

  @Override
  public Map<String, Object> getUISchemaMap() {
    Map<String, Object> map = new TreeMap<String, Object>(String::compareToIgnoreCase);
    map.put(EDIT_EMPLOYEE_PATH, currentRuntimeConfigService().getResolvedEditSchema());
    map.put(EMPLOYEE_DETAIL_PATH, currentRuntimeConfigService().getResolvedDetailSchema());
    return map;
  }

  private PersonnelProfileRuntimeConfigService currentRuntimeConfigService() {
    PersonnelProfileRuntimeConfigService bean = SpringContextUtil.getBean(PersonnelProfileRuntimeConfigService.class);
    return bean;
  }
}
