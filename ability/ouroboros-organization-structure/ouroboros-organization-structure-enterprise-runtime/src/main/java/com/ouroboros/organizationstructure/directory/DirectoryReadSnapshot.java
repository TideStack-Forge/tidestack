package com.ouroboros.organizationstructure.directory;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;

/** Accumulates one bounded, consistent remote read before the synchronization transaction. */
public final class DirectoryReadSnapshot {
  private final Map<String, EnterpriseDirectoryDepartment> departments = new LinkedHashMap<>();
  private final Map<String, EnterpriseDirectoryMember> members = new LinkedHashMap<>();

  public void department(EnterpriseDirectoryDepartment value) {
    var previous = departments.putIfAbsent(value.externalId(), value);
    if (previous != null && !previous.equals(value)) { throw new ValidationException("读取期间部门资料发生变化，请重新读取"); }
    checkLimit();
  }

  public void member(EnterpriseDirectoryMember value) {
    var previous = members.get(value.externalId());
    if (previous == null) { members.put(value.externalId(), value); }
    else {
      if (!previous.name().equals(value.name()) || !Objects.equals(previous.mobile(), value.mobile())
          || !Objects.equals(previous.email(), value.email()) || previous.active() != value.active()
          || !Objects.equals(previous.primaryDepartmentExternalId(), value.primaryDepartmentExternalId())) {
        throw new ValidationException("读取期间成员资料发生变化，请重新读取");
      }
      var memberships = new LinkedHashSet<>(previous.departmentExternalIds()); memberships.addAll(value.departmentExternalIds());
      members.put(value.externalId(), new EnterpriseDirectoryMember(value.externalId(), value.name(), value.mobile(), value.email(),
          List.copyOf(memberships), value.primaryDepartmentExternalId(), value.active()));
    }
    checkLimit();
  }

  public EnterpriseDirectorySnapshot complete() { return new EnterpriseDirectorySnapshot(List.copyOf(departments.values()), List.copyOf(members.values()), true); }

  private void checkLimit() {
    if (departments.size() + members.size() > 20000) { throw new ValidationException("企业目录超过本次读取上限"); }
  }

  public static JsonNode success(JsonNode response, String codeField) {
    if (response == null || !response.path(codeField).isIntegralNumber()) { throw new BusinessException("企业 API 未返回有效结果码"); }
    if (response.path(codeField).asLong() != 0) {
      throw new BusinessException("企业 API 拒绝读取目录，结果码: " + response.path(codeField).asLong());
    }
    return response;
  }

  public static List<JsonNode> items(JsonNode container, String field) {
    var value = container.path(field);
    if (!value.isArray()) { throw new BusinessException("企业目录响应缺少列表: " + field); }
    var result = new ArrayList<JsonNode>(); value.forEach(result::add); return List.copyOf(result);
  }

  public static String required(JsonNode value, String field) {
    var item = value.path(field);
    if ((!item.isTextual() && !item.isIntegralNumber()) || item.asText().isBlank()) {
      throw new BusinessException("企业目录响应缺少字段: " + field);
    }
    return item.asText();
  }

  public static String optional(JsonNode value, String field) {
    var item = value.path(field); return item.isMissingNode() || item.isNull() || item.asText().isBlank() ? null : required(value, field);
  }

  public static List<String> identifiers(JsonNode value, String field) {
    return items(value, field).stream().map(item -> {
      if ((!item.isIntegralNumber() && !item.isTextual()) || item.asText().isBlank()) {
        throw new BusinessException("企业目录成员部门标识无效");
      }
      return item.asText();
    }).distinct().toList();
  }
}
