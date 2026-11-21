package com.ouroboros.workflow.approval.dev.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.service.impl.BuiltinPositionDevelopService;
import com.ouroboros.dev.app.service.impl.BuiltinRoleDevelopService;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.workflow.approval.CandidateProvider;

class BuiltinOrganizationDesignTimeCandidateProviderTest {

  @Test
  void roleProviderShouldMapBuiltinRoleIdAndNameToCandidateItems() {
    BuiltinRoleModel role = new BuiltinRoleModel();
    role.setId("finance-approver");
    role.setName("财务审批员");
    BuiltinRoleDesignTimeCandidateProvider provider = new BuiltinRoleDesignTimeCandidateProvider(
        new StubBuiltinRoleDevelopService(role)
    );

    List<CandidateProvider.CandidateItem> items = provider.search("demo-app", null, "财务");

    assertEquals(1, items.size());
    assertEquals("finance-approver", items.get(0).getValue());
    assertEquals("财务审批员", items.get(0).getLabel());
  }

  @Test
  void positionProviderShouldMapBuiltinPositionKeyAndNameToCandidateItems() {
    PositionDefinition position = new PositionDefinition();
    position.setKey("acceptance-manager");
    position.setName("验收负责人");
    BuiltinPositionDesignTimeCandidateProvider provider = new BuiltinPositionDesignTimeCandidateProvider(
        new StubBuiltinPositionDevelopService(position)
    );

    List<CandidateProvider.CandidateItem> items = provider.search("demo-app", Collections.singletonList("acceptance-manager"));

    assertEquals(1, items.size());
    assertEquals("acceptance-manager", items.get(0).getValue());
    assertEquals("验收负责人", items.get(0).getLabel());
  }

  private static class StubBuiltinRoleDevelopService extends BuiltinRoleDevelopService {
    private final BuiltinRoleModel role;

    private StubBuiltinRoleDevelopService(BuiltinRoleModel role) {
      this.role = role;
    }

    @Override
    public List<?> search(Map<String, Object> searchParam) {
      return Collections.singletonList(role);
    }
  }

  private static class StubBuiltinPositionDevelopService extends BuiltinPositionDevelopService {
    private final PositionDefinition position;

    private StubBuiltinPositionDevelopService(PositionDefinition position) {
      this.position = position;
    }

    @Override
    public List<?> search(Map<String, Object> searchParam) {
      return Collections.singletonList(position);
    }
  }
}
