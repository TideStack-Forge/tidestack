package com.ouroboros.organizationstructure.directory.dingtalk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.util.Json;

class DingtalkDirectorySourceTest {
  private final EnterpriseRpcClient client = mock(EnterpriseRpcClient.class);
  private final EnterpriseRpcClient.Session session = mock(EnterpriseRpcClient.Session.class);
  private final ResolvedEnterpriseConnection connection = new ResolvedEnterpriseConnection("ding-main", Map.of("provider", "dingtalk"), null);
  private final DingtalkDirectorySource source = new DingtalkDirectorySource(client);

  @BeforeEach void setup() throws Exception {
    when(client.session(eq(connection), any())).thenReturn(session);
    when(session.invoke("dingtalk.contact.department.get", Map.of("dept_id", 1))).thenReturn(json("{\"errcode\":0,\"result\":{\"dept_id\":1,\"name\":\"公司\"}}"));
    when(session.invoke("dingtalk.contact.department.children", Map.of("dept_id", 1L))).thenReturn(json("{\"errcode\":0,\"result\":[{\"dept_id\":2,\"name\":\"研发\"}]}"));
    when(session.invoke("dingtalk.contact.department.children", Map.of("dept_id", 2L))).thenReturn(json("{\"errcode\":0,\"result\":[]}"));
  }

  @Test void traversesDepartmentsAndAllMemberPagesWithoutInventingPrimaryDepartment() throws Exception {
    var alice = "{\"userid\":\"alice\",\"name\":\"甲\",\"dept_id_list\":[1,2],\"active\":false}";
    when(session.invoke("dingtalk.contact.user.list", Map.of("dept_id", 1L, "cursor", 0L, "size", 100)))
        .thenReturn(json("{\"errcode\":0,\"result\":{\"list\":[" + alice + "],\"has_more\":true,\"next_cursor\":100}}"));
    when(session.invoke("dingtalk.contact.user.list", Map.of("dept_id", 1L, "cursor", 100L, "size", 100)))
        .thenReturn(json("{\"errcode\":0,\"result\":{\"list\":[{\"userid\":\"bob\",\"name\":\"乙\",\"dept_id_list\":[1]}],\"has_more\":false}}"));
    when(session.invoke("dingtalk.contact.user.list", Map.of("dept_id", 2L, "cursor", 0L, "size", 100)))
        .thenReturn(json("{\"errcode\":0,\"result\":{\"list\":[" + alice + "],\"has_more\":false}}"));
    var snapshot = source.load(connection, EnterpriseDeadline.afterMillis(5000));
    assertThat(snapshot.complete()).isTrue(); assertThat(snapshot.departments()).hasSize(2); assertThat(snapshot.members()).hasSize(2);
    assertThat(snapshot.members().getFirst().primaryDepartmentExternalId()).isNull();
    assertThat(snapshot.members().getFirst().active()).isTrue();
    verify(session).invoke("dingtalk.contact.user.list", Map.of("dept_id", 1L, "cursor", 100L, "size", 100));
  }

  @Test void rejectsStuckPaginationAndMissingCompletenessFlag() {
    when(session.invoke(eq("dingtalk.contact.user.list"), anyMap())).thenReturn(json("{\"errcode\":0,\"result\":{\"list\":[],\"has_more\":true,\"next_cursor\":0}}"));
    assertThatThrownBy(() -> source.load(connection, EnterpriseDeadline.afterMillis(5000)))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class).hasMessageContaining("未前进");
    when(session.invoke(eq("dingtalk.contact.user.list"), anyMap())).thenReturn(json("{\"errcode\":0,\"result\":{\"list\":[]}}"));
    assertThatThrownBy(() -> source.load(connection, EnterpriseDeadline.afterMillis(5000)))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class).hasMessageContaining("完整性");
  }

  @Test void installingThisJarContributesItsDirectorySourceWithoutApprovalModules() {
    var candidates = org.springframework.boot.context.annotation.ImportCandidates.load(
        org.springframework.boot.autoconfigure.AutoConfiguration.class, getClass().getClassLoader()).getCandidates();
    assertThat(candidates).contains(DingtalkDirectoryAutoConfiguration.class.getName());
    try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
      context.registerBean(EnterpriseRpcClient.class, () -> client);
      context.register(DingtalkDirectoryAutoConfiguration.class); context.refresh();
      var sources = new com.ouroboros.organizationstructure.directory.DirectorySources(
          List.copyOf(context.getBeansOfType(com.ouroboros.enterprise.directory.EnterpriseDirectorySource.class).values()));
      assertThat(sources.providers()).containsExactly("dingtalk");
      assertThat(sources.require("dingtalk")).isInstanceOf(DingtalkDirectorySource.class);
    }
  }

  private JsonNode json(String value) { return Json.toBean(JsonNode.class, value); }
}
