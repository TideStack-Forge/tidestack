package com.ouroboros.organizationstructure.directory;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.util.Json;

class DirectoryReadSnapshotTest {
  @Test void mergesRepeatedMembersAcrossDepartmentsWithoutChoosingPrimaryOrOverwritingProfiles() {
    var read = new DirectoryReadSnapshot();
    read.member(member("alice", "甲", List.of("rd")));
    read.member(member("alice", "甲", List.of("support", "rd")));
    var snapshot = read.complete();
    assertThat(snapshot.members()).hasSize(1);
    assertThat(snapshot.members().getFirst().departmentExternalIds()).containsExactly("rd", "support");
    assertThat(snapshot.members().getFirst().primaryDepartmentExternalId()).isNull();
    assertThatThrownBy(() -> read.member(member("alice", "changed", List.of("rd"))))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class);
  }

  @Test void conflictingDepartmentParentsCannotBecomeAnApplicableSnapshot() {
    var read = new DirectoryReadSnapshot();
    read.department(new EnterpriseDirectoryDepartment("rd", "root", "研发"));
    read.department(new EnterpriseDirectoryDepartment("rd", "root", "研发"));
    assertThat(read.complete().departments()).hasSize(1);
    assertThatThrownBy(() -> read.department(new EnterpriseDirectoryDepartment("rd", "another", "研发")))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class);
  }

  @Test void malformedResponseIsAnErrorAndNeverAnEmptySuccessfulDirectory() {
    var data = json("{\"errcode\":0,\"items\":[],\"id\":123,\"name\":\"研发\",\"departments\":[1,2,1],\"empty\":\"\"}");
    assertThat(DirectoryReadSnapshot.success(data, "errcode")).isSameAs(data);
    assertThat(DirectoryReadSnapshot.items(data, "items")).isEmpty();
    assertThat(DirectoryReadSnapshot.required(data, "id")).isEqualTo("123");
    assertThat(DirectoryReadSnapshot.identifiers(data, "departments")).containsExactly("1", "2");
    assertThat(DirectoryReadSnapshot.optional(data, "absent")).isNull();
    assertThat(DirectoryReadSnapshot.optional(data, "empty")).isNull();
    assertThat(DirectoryReadSnapshot.optional(data, "name")).isEqualTo("研发");
    assertThatThrownBy(() -> DirectoryReadSnapshot.success(null, "errcode")).isInstanceOf(com.ouroboros.exception.BusinessException.class);
    assertThatThrownBy(() -> DirectoryReadSnapshot.success(json("{}"), "errcode")).isInstanceOf(com.ouroboros.exception.BusinessException.class);
    assertThatThrownBy(() -> DirectoryReadSnapshot.required(data, "absent")).isInstanceOf(com.ouroboros.exception.BusinessException.class);
    assertThatThrownBy(() -> DirectoryReadSnapshot.identifiers(json("{\"items\":[true]}"), "items")).isInstanceOf(com.ouroboros.exception.BusinessException.class);
  }

  @Test void boundsTheReadBeforeItCanBecomeAnUnboundedSynchronizationPlan() {
    var read = new DirectoryReadSnapshot();
    for (int index = 0; index < 20000; index++) { read.department(new EnterpriseDirectoryDepartment("id" + index, null, "部门")); }
    assertThatThrownBy(() -> read.member(member("overflow", "甲", List.of("id1"))))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class).hasMessageContaining("上限");
  }

  private EnterpriseDirectoryMember member(String id, String name, List<String> departments) {
    return new EnterpriseDirectoryMember(id, name, null, null, departments, null, true);
  }
  private JsonNode json(String value) { return Json.toBean(JsonNode.class, value); }
}
