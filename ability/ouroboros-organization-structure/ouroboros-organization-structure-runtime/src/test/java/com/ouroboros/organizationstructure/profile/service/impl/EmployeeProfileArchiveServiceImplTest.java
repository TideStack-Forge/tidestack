package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.profile.dto.EmployeeProfileArchiveDTO;
import com.ouroboros.organizationstructure.profile.entity.EmployeeProfileValueEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EmployeeProfileArchiveServiceImplTest {

  @Test
  void splitShouldSeparateCoreFieldsFromProfileValuesAndMergeBack() {
    EmployeeProfileArchiveServiceImpl service = new EmployeeProfileArchiveServiceImpl();

    Map<String, Object> archive = new LinkedHashMap<String, Object>();
    archive.put("id", "employee-1");
    archive.put("name", "张三");
    archive.put("gender", "male");
    archive.put("mobile", "13800000000");
    archive.put("email", "zhangsan@example.com");
    archive.put("birthday", "2000-01-01");
    archive.put("isDeleted", Boolean.FALSE);
    archive.put("customTitle", "高级工程师");
    archive.put("customLevel", "P6");

    EmployeeProfileArchiveDTO split = service.split(archive);
    EmployeeEntity employee = split.getEmployee();

    assertEquals("employee-1", employee.getId());
    assertEquals("张三", employee.getName());
    assertEquals("male", employee.getGender());
    assertEquals("13800000000", employee.getMobile());
    assertEquals("zhangsan@example.com", employee.getEmail());
    assertEquals("2000-01-01", employee.getBirthday());
    assertEquals(Boolean.FALSE, employee.isDeleted());
    assertEquals(2, split.getProfileValues().size());
    assertFalse(split.getProfileValues().containsKey("id"));
    assertFalse(split.getProfileValues().containsKey("name"));

    EmployeeProfileValueEntity profileValue = service.toProfileValueEntity("employee-1", archive);
    assertEquals("employee-1", profileValue.getEmployeeId());
    assertEquals(split.getProfileValues(), profileValue.getProfileValues());

    Map<String, Object> merged = service.merge(employee, profileValue);
    assertEquals(archive, merged);
  }
}
