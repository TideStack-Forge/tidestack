package com.ouroboros.organizationstructure.directory.feishu;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.util.Json;

class FeishuDirectorySourceTest {
  private final EnterpriseRpcClient client = mock(EnterpriseRpcClient.class);
  private final EnterpriseRpcClient.Session session = mock(EnterpriseRpcClient.Session.class);
  private final ResolvedEnterpriseConnection connection = new ResolvedEnterpriseConnection("feishu-main", Map.of("provider", "feishu"), null);
  private final FeishuDirectorySource source = new FeishuDirectorySource(client);

  @BeforeEach void setup() throws Exception {
    when(client.session(eq(connection), any())).thenReturn(session);
    when(session.invoke("feishu.contact.department.get", Map.of("departmentId", "0", "department_id_type", "department_id")))
        .thenReturn(json("{\"code\":0,\"data\":{\"department\":{\"department_id\":\"0\",\"name\":\"公司\"}}}"));
  }

  @Test void readsAllPagesUsingStableUserIdsAndLeavesPrimaryDepartmentForAdministrator() throws Exception {
    when(session.invoke(eq("feishu.contact.department.children"), anyMap())).thenAnswer(call -> {
      Map<?, ?> params = call.getArgument(1);
      if (!"0".equals(params.get("departmentId"))) { return page("[]", false, null); }
      return "".equals(params.get("page_token"))
          ? page("[{\"department_id\":\"rd\",\"parent_department_id\":\"0\",\"name\":\"研发\"}]", true, "next")
          : page("[]", false, null);
    });
    when(session.invoke(eq("feishu.contact.user.list"), anyMap())).thenAnswer(call -> {
      Map<?, ?> params = call.getArgument(1);
      assertThat(params.get("user_id_type")).isEqualTo("user_id");
      assertThat(params.get("department_id_type")).isEqualTo("department_id");
      return page("[{\"user_id\":\"alice\",\"name\":\"甲\",\"department_ids\":[\"0\",\"rd\"],\"status\":{\"is_frozen\":true}}]", false, null);
    });
    var snapshot = source.load(connection, EnterpriseDeadline.afterMillis(5000));
    assertThat(snapshot.complete()).isTrue(); assertThat(snapshot.departments()).hasSize(2); assertThat(snapshot.members()).hasSize(1);
    assertThat(snapshot.members().getFirst().externalId()).isEqualTo("alice");
    assertThat(snapshot.members().getFirst().primaryDepartmentExternalId()).isNull();
    assertThat(snapshot.members().getFirst().active()).isTrue();
    verify(session).invoke("feishu.contact.department.children", Map.of("departmentId", "0", "department_id_type", "department_id", "page_size", 50, "page_token", "next"));
  }

  @Test void rejectsRepeatedPageTokenAndDeniesPartialDirectory() {
    when(session.invoke(eq("feishu.contact.department.children"), anyMap())).thenReturn(page("[]", true, "same"));
    assertThatThrownBy(() -> source.load(connection, EnterpriseDeadline.afterMillis(5000)))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class).hasMessageContaining("未前进");
    when(session.invoke(eq("feishu.contact.department.children"), anyMap())).thenReturn(json("{\"code\":99991672}"));
    assertThatThrownBy(() -> source.load(connection, EnterpriseDeadline.afterMillis(5000)))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class).hasMessageContaining("99991672");
  }

  private JsonNode page(String items, boolean more, String token) {
    return json("{\"code\":0,\"data\":{\"items\":" + items + ",\"has_more\":" + more + (token == null ? "" : ",\"page_token\":\"" + token + "\"") + "}}");
  }
  @Test void installingThisJarContributesItsDirectorySourceWithoutApprovalModules() {
    var candidates = org.springframework.boot.context.annotation.ImportCandidates.load(
        org.springframework.boot.autoconfigure.AutoConfiguration.class, getClass().getClassLoader()).getCandidates();
    assertThat(candidates).contains(FeishuDirectoryAutoConfiguration.class.getName());
    try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      context.registerBean(EnterpriseRpcClient.class, () -> client);
      context.register(FeishuDirectoryAutoConfiguration.class); context.refresh();
      var sources = new com.ouroboros.organizationstructure.directory.DirectorySources(
          List.copyOf(context.getBeansOfType(com.ouroboros.enterprise.directory.EnterpriseDirectorySource.class).values()));
      assertThat(sources.providers()).containsExactly("feishu");
      assertThat(sources.require("feishu")).isInstanceOf(FeishuDirectorySource.class);
    }
  }

  private JsonNode json(String value) { return Json.toBean(JsonNode.class, value); }
}
