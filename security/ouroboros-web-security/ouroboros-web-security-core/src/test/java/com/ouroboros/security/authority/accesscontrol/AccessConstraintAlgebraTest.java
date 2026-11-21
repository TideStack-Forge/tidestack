package com.ouroboros.security.authority.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AccessConstraintAlgebraTest {
  @Test
  void fieldIntersectionAndUnionMatchSetMembershipIncludingComplements() {
    var constraints = List.of(FieldConstraint.all(), FieldConstraint.none(), FieldConstraint.only(Set.of("id", "name")),
        FieldConstraint.only(Set.of("id", "cost")), new FieldConstraint(FieldConstraint.Mode.ALL, Set.of(), Set.of("cost")));
    for (var a : constraints) {
      for (var b : constraints) {
        for (String field : List.of("id", "name", "cost", "new-field")) {
          assertThat(a.and(b).allows(field)).as("%s AND %s: %s", a, b, field).isEqualTo(a.allows(field) && b.allows(field));
          assertThat(a.or(b).allows(field)).as("%s OR %s: %s", a, b, field).isEqualTo(a.allows(field) || b.allows(field));
        }
      }
    }
  }

  @Test
  void rowsPreserveAllNoneAndFiltersAndFlattenOperands() {
    var filter = RowConstraint.filter(Map.of("department", "d1"));
    assertThat(filter.or(RowConstraint.all())).isEqualTo(RowConstraint.all());
    assertThat(filter.and(RowConstraint.all())).isEqualTo(filter);
    assertThat(filter.and(RowConstraint.none())).isEqualTo(RowConstraint.none());
    assertThat(filter.or(RowConstraint.none())).isEqualTo(filter);
    var combined = filter.or(RowConstraint.filter(Map.of("department", "d2")))
        .or(RowConstraint.filter(Map.of("department", "d3")));
    assertThat(combined.condition()).isEqualTo(Map.of("OR", List.of(Map.of("department", "d1"),
        Map.of("department", "d2"), Map.of("department", "d3"))));
    assertThatThrownBy(() -> RowConstraint.filter(Map.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RowConstraint.filter(Map.of("script", new Object()))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void permissionsResponseSerializesFiniteAndDeniedConstraintsWithoutWidening() {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var finite = new ResolvedAccessControlParams(FieldConstraint.only(Set.of("clientId")), RowConstraint.all());
    var json = mapper.valueToTree(finite);
    assertThat(json.path("allowedFields").get(0).asText()).isEqualTo("clientId");
    assertThat(json.has("disallowedFields")).isFalse();
    assertThatThrownBy(finite::getDisallowedFields).hasMessageContaining("允许集");
    var denied = mapper.valueToTree(new ResolvedAccessControlParams(FieldConstraint.none(), RowConstraint.none()));
    assertThat(denied.path("fieldConstraint").path("mode").asText()).isEqualTo("NONE");
    assertThat(denied.path("rowConstraint").path("mode").asText()).isEqualTo("NONE");
    assertThat(denied.has("allowedFields")).isFalse();
    assertThat(denied.has("filters")).isFalse();
  }

  @Test
  void absentAndEmptyIntersectionsCannotBeProjectedAsUnlimitedAccess() {
    assertThat(AccessControlParams.empty().getFieldConstraint()).isEqualTo(FieldConstraint.none());
    assertThat(AccessControlParams.empty().getRowConstraint()).isEqualTo(RowConstraint.none());
    assertThatThrownBy(() -> AccessControlParams.empty().getFilters()).hasMessageContaining("没有可访问的数据行");
    var disjoint = AccessControlParams.and(new ResolvedAccessControlParams(FieldConstraint.only(Set.of("a")), RowConstraint.all()),
        new ResolvedAccessControlParams(FieldConstraint.only(Set.of("b")), RowConstraint.all()));
    assertThat(disjoint.getFieldConstraint()).isEqualTo(FieldConstraint.none());
    assertThatThrownBy(disjoint::getAllowedFields).hasMessageContaining("没有可访问的业务字段");
  }
}
