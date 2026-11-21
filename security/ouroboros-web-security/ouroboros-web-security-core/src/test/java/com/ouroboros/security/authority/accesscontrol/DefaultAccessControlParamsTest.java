package com.ouroboros.security.authority.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DefaultAccessControlParamsTest {
  @Test
  void absentRestrictionsAllowAllFieldsAndRowsEvenWhenFieldControlsAreAvailable() {
    var params = new DefaultAccessControlParams(definition(), Map.of());
    assertThat(params.getFieldConstraint()).isEqualTo(FieldConstraint.all());
    assertThat(params.getFieldConstraint().allows("newBusinessField")).isTrue();
    assertThat(params.getAllowedFields()).containsExactly("*");
    assertThat(params.getDisallowedFields()).isEmpty();
    assertThat(params.getRowConstraint()).isEqualTo(RowConstraint.all());
    assertThat(params.getFilters()).isEmpty();
  }

  @Test
  void emptyFieldSelectionKeepsTheLegacyUnrestrictedDefault() {
    var params = new DefaultAccessControlParams(definition(), Map.of("$fieldControl", List.of()));
    assertThat(params.getFieldConstraint()).isEqualTo(FieldConstraint.all());
    assertThat(params.getDisallowedFields()).isEmpty();
    assertThat(params.getRowConstraint()).isEqualTo(RowConstraint.all());
  }

  @Test
  void missingSettingsAreEquivalentToAnEmptySettingsObject() {
    var params = new DefaultAccessControlParams(definition(), null);
    assertThat(params.getFieldConstraint()).isEqualTo(FieldConstraint.all());
    assertThat(params.getRowConstraint()).isEqualTo(RowConstraint.all());
  }

  @Test
  void explicitFieldSelectionRestrictsFieldsWithoutAddingARowRestriction() {
    var params = new DefaultAccessControlParams(definition(), Map.of("$fieldControl", List.of("name")));
    assertThat(params.getFieldConstraint().allows("id")).isTrue();
    assertThat(params.getFieldConstraint().allows("name")).isTrue();
    assertThat(params.getFieldConstraint().allows("salary")).isFalse();
    assertThat(params.getFieldConstraint().allows("newBusinessField")).isFalse();
    assertThat(params.getDisallowedFields()).containsExactly("salary");
    assertThat(params.getRowConstraint()).isEqualTo(RowConstraint.all());
  }

  @Test
  void explicitlyRequiredManagementFieldSelectionDoesNotAcquireUnselectedFields() {
    var definition = definition();
    when(definition.isFieldSelectionRequired()).thenReturn(true);
    var params = new DefaultAccessControlParams(definition, Map.of());
    assertThat(params.getAllowedFields()).containsExactly("id");
    assertThat(params.getFieldConstraint().allows("salary")).isFalse();
  }

  private AccessControlDefine definition() {
    var definition = mock(AccessControlDefine.class);
    when(definition.getCanControlFields()).thenReturn(true);
    when(definition.getCommonFields()).thenReturn(List.of(new AccessControlDefine.Field("id", "标识", "String", null)));
    when(definition.getControlledFields()).thenReturn(List.of(
        new AccessControlDefine.Field("name", "姓名", "String", null),
        new AccessControlDefine.Field("salary", "薪资", "String", null)));
    when(definition.getFilterDefine()).thenReturn(List.of());
    return definition;
  }
}
