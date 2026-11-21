package com.ouroboros.organizationstructure.directory.feishu;

import java.util.*;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.directory.DirectoryReadSnapshot;
import static com.ouroboros.organizationstructure.directory.DirectoryReadSnapshot.*;

public final class FeishuDirectorySource implements EnterpriseDirectorySource {
  private final EnterpriseRpcClient client;
  public FeishuDirectorySource(EnterpriseRpcClient client) { this.client = client; }
  @Override public String provider() { return "feishu"; }

  @Override public EnterpriseDirectorySnapshot load(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    var session = client.session(connection, deadline); var snapshot = new DirectoryReadSnapshot();
    var root = success(session.invoke("feishu.contact.department.get", Map.of("departmentId", "0", "department_id_type", "department_id")), "code").path("data").path("department");
    snapshot.department(new EnterpriseDirectoryDepartment(required(root, "department_id"), null, required(root, "name")));
    var pending = new ArrayDeque<String>(); pending.add("0"); var visited = new HashSet<String>();
    while (!pending.isEmpty()) {
      String id = pending.remove();
      if (!visited.add(id) || visited.size() > 20000) { throw new ValidationException("飞书目录层级重复或超过读取上限"); }
      String cursor = ""; var cursors = new HashSet<String>();
      while (true) {
        if (!cursors.add(cursor) || cursors.size() > 400) { throw new ValidationException("飞书部门分页未前进或超过读取上限"); }
        var page = success(session.invoke("feishu.contact.department.children", Map.of("departmentId", id, "department_id_type", "department_id", "page_size", 50, "page_token", cursor)), "code").path("data");
        for (var child : items(page, "items")) {
          String childId = required(child, "department_id");
          snapshot.department(new EnterpriseDirectoryDepartment(childId, required(child, "parent_department_id"), required(child, "name"))); pending.add(childId);
        }
        cursor = next(page); if (cursor == null) { break; }
      }
      cursor = ""; cursors.clear();
      while (true) {
        if (!cursors.add(cursor) || cursors.size() > 400) { throw new ValidationException("飞书成员分页未前进或超过读取上限"); }
        var page = success(session.invoke("feishu.contact.user.list", Map.of("department_id", id, "department_id_type", "department_id", "user_id_type", "user_id", "page_size", 50, "page_token", cursor)), "code").path("data");
        for (var user : items(page, "items")) {
          snapshot.member(new EnterpriseDirectoryMember(required(user, "user_id"), required(user, "name"), optional(user, "mobile"), optional(user, "email"),
              identifiers(user, "department_ids"), null, !user.path("status").path("is_resigned").asBoolean(false)));
        }
        cursor = next(page); if (cursor == null) { break; }
      }
    }
    return snapshot.complete();
  }

  private String next(com.fasterxml.jackson.databind.JsonNode page) {
    if (!page.path("has_more").isBoolean()) { throw new ValidationException("飞书目录响应缺少分页完整性标记"); }
    return page.path("has_more").asBoolean() ? required(page, "page_token") : null;
  }
}
