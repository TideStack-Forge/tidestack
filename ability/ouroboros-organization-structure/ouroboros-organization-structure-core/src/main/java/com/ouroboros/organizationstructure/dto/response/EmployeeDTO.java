package com.ouroboros.organizationstructure.dto.response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.Position;

public class EmployeeDTO {
  private static final Set<String> RESERVED_PROFILE_KEYS = reservedProfileKeys();

  private String id;
  private String name;
  private String userId;
  private String source;
  private String departmentName;
  private String positionName;         // 主岗位名称
  private List<DepartmentInfo> departments; // 部门列表（主部门在第一位）
  private List<PositionInfo> positions;     // 岗位列表（主岗位在第一位）
  private String gender;
  private String birthday;
  private String mobile;
  private String email;
  private final Map<String, Object> profileValues = new LinkedHashMap<String, Object>();

  public static class DepartmentInfo {
    private String id;
    private String name;
    
    public DepartmentInfo(String id, String name) {
      this.id = id;
      this.name = name;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
  }
  
  public static class PositionInfo {
    private String id;
    private String name;
    
    public PositionInfo(String id, String name) {
      this.id = id;
      this.name = name;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
  }

  public static EmployeeDTO from(Employee employee) {
    return from(employee, Collections.<String, Object>emptyMap());
  }

  public static EmployeeDTO from(Employee employee, Map<String, Object> profileValues) {
    EmployeeDTO dto = new EmployeeDTO();
    dto.setId(employee.getId());
    dto.setName(employee.getName());
    dto.setUserId(employee.getUser());
    dto.setSource(employee.source());
    dto.setGender(employee.getGender());
    dto.setBirthday(employee.getBirthday() != null ? employee.getBirthday().toString() : null);
    dto.setMobile(employee.getMobile());
    dto.setEmail(employee.getEmail());
    dto.profileValues.putAll(safeProfileValues(profileValues));
    
    // 获取所有当前有效的任职
    List<Appointment> appointments = employee.getCurrentAppointments();
    
    // 找出主岗位
    Appointment primaryAppointment = employee.getPrimaryAppointment();
    
    // 构建岗位列表（主岗位在第一位）
    List<PositionInfo> positionList = new ArrayList<>();
    Set<String> addedPositionIds = new LinkedHashSet<>();
    
    if (primaryAppointment != null) {
      Position primaryPosition = primaryAppointment.getPositionDetail();
      if (primaryPosition != null) {
        dto.setPositionName(primaryPosition.getName());
        positionList.add(new PositionInfo(primaryPosition.getId(), primaryPosition.getName()));
        addedPositionIds.add(primaryPosition.getId());
      }
    }
    
    // 添加其他岗位
    for (Appointment appointment : appointments) {
      if (appointment != primaryAppointment) {
        Position position = appointment.getPositionDetail();
        if (position != null && !addedPositionIds.contains(position.getId())) {
          positionList.add(new PositionInfo(position.getId(), position.getName()));
          addedPositionIds.add(position.getId());
        }
      }
    }
    dto.setPositions(positionList);
    
    // 构建部门列表（主部门在第一位）
    List<DepartmentInfo> departmentList = new ArrayList<>();
    Set<String> addedDepartmentIds = new LinkedHashSet<>();
    
    if (primaryAppointment != null) {
      Position primaryPosition = primaryAppointment.getPositionDetail();
      if (primaryPosition != null && primaryPosition.getParentDetail() instanceof Department) {
        Department primaryDepartment = (Department) primaryPosition.getParentDetail();
        if (primaryDepartment != null) {
          dto.setDepartmentName(primaryDepartment.getName());
          departmentList.add(new DepartmentInfo(primaryDepartment.getId(), primaryDepartment.getName()));
          addedDepartmentIds.add(primaryDepartment.getId());
        }
      }
    }
    
    // 添加其他部门
    for (Appointment appointment : appointments) {
      if (appointment != primaryAppointment) {
        Position position = appointment.getPositionDetail();
        if (position != null && position.getParentDetail() instanceof Department) {
          Department department = (Department) position.getParentDetail();
          if (department != null && !addedDepartmentIds.contains(department.getId())) {
            departmentList.add(new DepartmentInfo(department.getId(), department.getName()));
            addedDepartmentIds.add(department.getId());
          }
        }
      }
    }
    dto.setDepartments(departmentList);
    
    return dto;
  }

  @JsonAnyGetter
  public Map<String, Object> getProfileValues() {
    return Collections.unmodifiableMap(profileValues);
  }

  @JsonIgnore
  public Map<String, Object> toProfileValues() {
    return new LinkedHashMap<String, Object>(profileValues);
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getDepartmentName() { return departmentName; }
  public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
  public String getPositionName() { return positionName; }
  public void setPositionName(String positionName) { this.positionName = positionName; }
  public List<DepartmentInfo> getDepartments() { return departments; }
  public void setDepartments(List<DepartmentInfo> departments) { this.departments = departments; }
  public List<PositionInfo> getPositions() { return positions; }
  public void setPositions(List<PositionInfo> positions) { this.positions = positions; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
  public String getBirthday() { return birthday; }
  public void setBirthday(String birthday) { this.birthday = birthday; }
  public String getMobile() { return mobile; }
  public void setMobile(String mobile) { this.mobile = mobile; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  private static Map<String, Object> safeProfileValues(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    if (source == null) {
      return result;
    }
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (entry.getKey() != null && !RESERVED_PROFILE_KEYS.contains(entry.getKey())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  private static Set<String> reservedProfileKeys() {
    Set<String> keys = new HashSet<String>();
    Collections.addAll(keys,
        "id",
        "name",
        "userId",
        "source",
        "departmentName",
        "positionName",
        "departments",
        "positions",
        "gender",
        "birthday",
        "mobile",
        "email");
    return Collections.unmodifiableSet(keys);
  }
}
