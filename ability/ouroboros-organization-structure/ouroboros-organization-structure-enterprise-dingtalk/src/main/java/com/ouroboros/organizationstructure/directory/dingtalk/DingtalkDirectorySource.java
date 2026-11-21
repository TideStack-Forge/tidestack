package com.ouroboros.organizationstructure.directory.dingtalk;

import java.util.*;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.directory.DirectoryReadSnapshot;
import static com.ouroboros.organizationstructure.directory.DirectoryReadSnapshot.*;

public final class DingtalkDirectorySource implements EnterpriseDirectorySource {
  private final EnterpriseRpcClient client;
  public DingtalkDirectorySource(EnterpriseRpcClient client) { this.client = client; }
  @Override public String provider() { return "dingtalk"; }

  @Override public EnterpriseDirectorySnapshot load(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    var session = client.session(connection, deadline);
    var snapshot = new DirectoryReadSnapshot();
    var root = success(session.invoke("dingtalk.contact.department.get", Map.of("dept_id", 1)), "errcode").path("result");
    snapshot.department(new EnterpriseDirectoryDepartment(required(root, "dept_id"), null, required(root, "name")));
    var pending = new ArrayDeque<String>(); pending.add("1"); var visited = new HashSet<String>();
    while (!pending.isEmpty()) {
      String id = pending.remove();
      if (!visited.add(id) || visited.size() > 20000) { throw new ValidationException("钉钉目录层级重复或超过读取上限"); }
      var children = success(session.invoke("dingtalk.contact.department.children", Map.of("dept_id", Long.parseLong(id))), "errcode");
      for (var child : items(children, "result")) {
        String childId = required(child, "dept_id");
        snapshot.department(new EnterpriseDirectoryDepartment(childId, id, required(child, "name"))); pending.add(childId);
      }
      long cursor = 0; var cursors = new HashSet<Long>();
      while (true) {
        if (!cursors.add(cursor) || cursors.size() > 200) { throw new ValidationException("钉钉成员分页未前进或超过读取上限"); }
        var page = success(session.invoke("dingtalk.contact.user.list", Map.of("dept_id", Long.parseLong(id), "cursor", cursor, "size", 100)), "errcode").path("result");
        for (var user : items(page, "list")) {
          snapshot.member(new EnterpriseDirectoryMember(required(user, "userid"), required(user, "name"), optional(user, "mobile"), optional(user, "email"),
              identifiers(user, "dept_id_list"), null, true));
        }
        if (!page.path("has_more").isBoolean()) { throw new ValidationException("钉钉成员响应缺少分页完整性标记"); }
        if (!page.path("has_more").asBoolean()) { break; }
        if (!page.path("next_cursor").isIntegralNumber()) { throw new ValidationException("钉钉成员响应缺少下页游标"); }
        cursor = page.path("next_cursor").asLong();
      }
    }
    return snapshot.complete();
  }
}
