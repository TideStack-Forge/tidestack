package com.ouroboros.businessmodel.security;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.BaseAuthority;

/** Publishes definitions for the models constructed directly by runtime tests. */
public final class BehaviorFixtureAuthorityProvider implements AuthorityProvider {
  private static final List<Authority> DEFINITIONS = definitions();

  private static List<Authority> definitions() {
    var definitions = new ArrayList<Authority>();
    for (String model : List.of("sales.Order", "sales.Customer", "sales.Reviewer", "inventory.Stock",
        "cases.Document", "exportJourney.Order", "performance.Order", "billing.Invoice", "shipping.Dispatch")) {
      for (String action : List.of("add", "approve", "batchDelete", "delete", "existingPermission", "export", "forbidden",
          "import", "inspect", "processCallback", "reserve", "reserveStock", "search", "searchDeleted", "searchArchive",
          "searchHistory", "serverSideExport", "submit", "submitFlow", "update", "workflow", "behaviors.inspect")) {
        definitions.add(definition(model + "." + action));
      }
      for (String event : List.of("submitted", "approved", "recordsCreated", "recordsUpdated", "recordsDeleted",
          "workflowStarted", "workflowApproved", "workflowRejected", "workflowWithdrawn", "workflowCancelled",
          "workflowSuspended", "workflowResumed", "workflowFailed", "workflowTaskCreated", "workflowTaskActionCompleted",
          "workflowTaskTimeoutReminded")) {
        definitions.add(definition(model + ".events." + event + ".read"));
        definitions.add(definition(model + ".events." + event + ".emit"));
      }
    }
    return List.copyOf(definitions);
  }

  private static Authority definition(String name) {
    var definition = new BaseAuthority();
    definition.setFullName(name);
    return definition;
  }

  @Override
  public List<Authority> getAuthority(String name) {
    return DEFINITIONS.stream().filter(definition -> definition.getFullName().equals(name)).toList();
  }

  @Override
  public List<Authority> getAllAuthorities() { return DEFINITIONS; }
}
