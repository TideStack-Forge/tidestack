package com.ouroboros.organizationstructure.directory.wecom;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.util.Json;

class WecomDirectorySourceTest {
  private final EnterpriseRpcClient client = mock(EnterpriseRpcClient.class);
  private final EnterpriseRpcClient.Session session = mock(EnterpriseRpcClient.Session.class);
  private final ResolvedEnterpriseConnection connection = new ResolvedEnterpriseConnection("wecom-main", Map.of("provider", "wecom"), null);
  private final WecomDirectorySource source = new WecomDirectorySource(client);

  @BeforeEach void setup() throws Exception { when(client.session(eq(connection), any())).thenReturn(session); }

  @Test void readsEveryDepartmentAndMergesMembershipWithoutTreatingDisabledAccountsAsDepartures() throws Exception {
    when(session.invoke("wecom.contact.department.list", Map.of())).thenReturn(json("""
        {"errcode":0,"department":[{"id":1,"parentid":0,"name":"公司"},{"id":2,"parentid":1,"name":"研发"}]}
        """));
    var alice = """
        {"userid":"alice","name":"甲","department":[1,2],"main_department":1,"status":2}
        """;
    when(session.invoke("wecom.contact.user.list", Map.of("department_id", 1L))).thenReturn(json("{\"errcode\":0,\"userlist\":[" + alice + "]}"));
    when(session.invoke("wecom.contact.user.list", Map.of("department_id", 2L))).thenReturn(json("{\"errcode\":0,\"userlist\":[" + alice +
        ",{\"userid\":\"left\",\"name\":\"乙\",\"department\":[2],\"main_department\":2,\"status\":5}]}"));
    var snapshot = source.load(connection, EnterpriseDeadline.afterMillis(5000));
    assertThat(snapshot.complete()).isTrue(); assertThat(snapshot.departments()).hasSize(2); assertThat(snapshot.members()).hasSize(2);
    var first = snapshot.members().getFirst();
    assertThat(first.externalId()).isEqualTo("alice"); assertThat(first.departmentExternalIds()).containsExactly("1", "2");
    assertThat(first.primaryDepartmentExternalId()).isEqualTo("1"); assertThat(first.active()).isTrue();
    assertThat(snapshot.members().getLast().active()).isFalse();
    verify(session).invoke("wecom.contact.user.list", Map.of("department_id", 2L));
  }

  @Test void deniesUnauthorizedOrIncompleteReadInsteadOfReturningEmptySuccess() {
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"errcode\":48002}"));
    assertThatThrownBy(() -> source.load(connection, EnterpriseDeadline.afterMillis(5000)))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class).hasMessageContaining("48002");
    when(session.invoke(anyString(), anyMap())).thenReturn(json("{\"errcode\":0}"));
    assertThatThrownBy(() -> source.load(connection, EnterpriseDeadline.afterMillis(5000)))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class).hasMessageContaining("department");
  }

  @Test void installingThisJarContributesItsDirectorySourceWithoutApprovalModules() {
    var candidates = org.springframework.boot.context.annotation.ImportCandidates.load(
        org.springframework.boot.autoconfigure.AutoConfiguration.class, getClass().getClassLoader()).getCandidates();
    assertThat(candidates).contains(WecomDirectoryAutoConfiguration.class.getName());
    try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      context.registerBean(EnterpriseRpcClient.class, () -> client);
      context.register(WecomDirectoryAutoConfiguration.class); context.refresh();
      var sources = new com.ouroboros.organizationstructure.directory.DirectorySources(
          List.copyOf(context.getBeansOfType(com.ouroboros.enterprise.directory.EnterpriseDirectorySource.class).values()));
      assertThat(sources.providers()).containsExactly("wecom");
      assertThat(sources.require("wecom")).isInstanceOf(WecomDirectorySource.class);
    }
  }

  private JsonNode json(String value) { return Json.toBean(JsonNode.class, value); }
}
