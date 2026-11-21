package com.ouroboros.organizationstructure.profile.service.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.profile.dto.EmployeeProfileArchiveDTO;
import com.ouroboros.organizationstructure.profile.entity.EmployeeProfileValueEntity;
import com.ouroboros.organizationstructure.profile.service.EmployeeProfileArchiveService;
import com.ouroboros.util.Json;

/**
 * 员工档案核心/扩展字段拆分
 */
@Service
public class EmployeeProfileArchiveServiceImpl implements EmployeeProfileArchiveService {

  private static final Set<String> CORE_FIELD_NAMES = buildCoreFieldNames();

  @Override
  public EmployeeProfileArchiveDTO split(Map<String, Object> archiveValues) {
    Map<String, Object> source = archiveValues == null ? Collections.<String, Object>emptyMap() : archiveValues;
    EmployeeProfileArchiveDTO dto = new EmployeeProfileArchiveDTO();
    dto.setEmployee(toEmployeeEntity(source));
    dto.setProfileValues(extractProfileValues(source));
    return dto;
  }

  @Override
  public EmployeeEntity toEmployeeEntity(Map<String, Object> archiveValues) {
    Map<String, Object> source = archiveValues == null ? Collections.<String, Object>emptyMap() : archiveValues;
    Map<String, Object> coreValues = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (CORE_FIELD_NAMES.contains(entry.getKey())) {
        coreValues.put(entry.getKey(), entry.getValue());
      }
    }
    EmployeeEntity entity = Json.tryToBean(EmployeeEntity.class, Json.toJsonString(coreValues)).getOrElse(EmployeeEntity::new);
    return entity;
  }

  @Override
  public EmployeeProfileValueEntity toProfileValueEntity(String employeeId, Map<String, Object> archiveValues) {
    EmployeeProfileValueEntity entity = new EmployeeProfileValueEntity();
    entity.setEmployeeId(employeeId);
    entity.setProfileValues(extractProfileValues(archiveValues));
    return entity;
  }

  @Override
  public Map<String, Object> merge(EmployeeEntity employee, EmployeeProfileValueEntity profileValue) {
    Map<String, Object> merged = new LinkedHashMap<String, Object>();
    if (employee != null) {
      merged.putAll(extractCoreValues(employee));
    } else if (profileValue != null && profileValue.getEmployeeId() != null) {
      merged.put("id", profileValue.getEmployeeId());
    }
    if (profileValue != null && !profileValue.getProfileValues().isEmpty()) {
      merged.putAll(profileValue.getProfileValues());
    }
    return merged;
  }

  private Map<String, Object> extractProfileValues(Map<String, Object> archiveValues) {
    Map<String, Object> source = archiveValues == null ? Collections.<String, Object>emptyMap() : archiveValues;
    Map<String, Object> profileValues = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (!CORE_FIELD_NAMES.contains(entry.getKey())) {
        profileValues.put(entry.getKey(), entry.getValue());
      }
    }
    return profileValues;
  }

  private Map<String, Object> extractCoreValues(EmployeeEntity employee) {
    if (employee == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> coreValues = new LinkedHashMap<String, Object>();
    for (Field field : EmployeeEntity.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      String fieldName = field.getName();
      try {
        field.setAccessible(true);
        Object value = field.get(employee);
        if (value != null) {
          coreValues.put(fieldName, value);
        }
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("无法读取员工核心字段: " + fieldName, e);
      }
    }
    return coreValues;
  }

  private static Set<String> buildCoreFieldNames() {
    Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (Field field : EmployeeEntity.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        names.add(field.getName());
      }
    }
    return names;
  }
}
