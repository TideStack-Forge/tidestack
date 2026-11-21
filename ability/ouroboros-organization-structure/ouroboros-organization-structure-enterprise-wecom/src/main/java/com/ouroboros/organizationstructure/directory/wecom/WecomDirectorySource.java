package com.ouroboros.organizationstructure.directory.wecom;

import java.util.*;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.organizationstructure.directory.DirectoryReadSnapshot;
import static com.ouroboros.organizationstructure.directory.DirectoryReadSnapshot.*;

public final class WecomDirectorySource implements EnterpriseDirectorySource {
  private final EnterpriseRpcClient client;
  public WecomDirectorySource(EnterpriseRpcClient client) { this.client = client; }
  @Override public String provider() { return "wecom"; }

  @Override public EnterpriseDirectorySnapshot load(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    var session = client.session(connection, deadline); var snapshot = new DirectoryReadSnapshot();
    var response = success(session.invoke("wecom.contact.department.list", Map.of()), "errcode");
    for (var department : items(response, "department")) {
      String id = required(department, "id"); String parent = required(department, "parentid");
      snapshot.department(new EnterpriseDirectoryDepartment(id, "0".equals(parent) ? null : parent, required(department, "name")));
      var users = success(session.invoke("wecom.contact.user.list", Map.of("department_id", Long.parseLong(id))), "errcode");
      for (var user : items(users, "userlist")) {
        var departments = identifiers(user, "department"); String primary = optional(user, "main_department");
        snapshot.member(new EnterpriseDirectoryMember(required(user, "userid"), required(user, "name"), optional(user, "mobile"), optional(user, "email"),
            departments, departments.contains(primary) ? primary : null, user.path("status").asInt() != 5));
      }
    }
    return snapshot.complete();
  }
}
