package com.ouroboros.security.rbac.accesscontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.security.rbac.accesscontrol.filter.setting.CompanyFilterSettingSchemaGenerator;
import com.ouroboros.security.rbac.accesscontrol.filter.setting.DepartmentFilterSettingSchemaGenerator;
import com.ouroboros.security.rbac.accesscontrol.filter.setting.EmbedFilterSettingSchemaGenerator;
import com.ouroboros.security.rbac.accesscontrol.filter.setting.EmployeeFilterSettingSchemaGenerator;
import com.ouroboros.security.rbac.accesscontrol.setting.FieldControlSettingSchemaGenerator;
import com.ouroboros.uischema.schema.SchemaNode;

class AccessControlSchemaGeneratorsTest {

  @Test
  void companyGeneratorShouldSupportByCompanyFilterAndGenerateSchema() {
    var define = mock(AccessControlDefine.class);
    var filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn("$byCompany");

    var generator = new CompanyFilterSettingSchemaGenerator();
    assertTrue(generator.supports(define, filter));
    assertEquals(1, generator.generate(define, filter).size());
  }

  @Test
  void departmentGeneratorShouldSupportByDepartmentFilterAndGenerateSchema() {
    var define = mock(AccessControlDefine.class);
    var filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn("$byDepartment");

    var generator = new DepartmentFilterSettingSchemaGenerator();
    assertTrue(generator.supports(define, filter));
    assertEquals(1, generator.generate(define, filter).size());
  }

  @Test
  void employeeGeneratorShouldSupportByEmployeeFilterAndGenerateSchema() {
    var define = mock(AccessControlDefine.class);
    var filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn("$byEmployee");

    var generator = new EmployeeFilterSettingSchemaGenerator();
    assertTrue(generator.supports(define, filter));
    assertEquals(1, generator.generate(define, filter).size());
  }

  @Test
  void embedGeneratorShouldUseEmbeddedSchemaDirectly() {
    var define = mock(AccessControlDefine.class);
    var filter = mock(AccessControlFilterDefine.class);
    var embedded = Collections.singletonList(mock(SchemaNode.class));
    when(filter.getSettingSchema()).thenReturn(embedded);

    var generator = new EmbedFilterSettingSchemaGenerator();
    assertTrue(generator.supports(define, filter));
    assertEquals(embedded, generator.generate(define, filter));
  }

  @Test
  void fieldControlGeneratorShouldCheckFlagAndGenerateSingleNode() {
    var define = mock(AccessControlDefine.class);
    when(define.getCanControlFields()).thenReturn(Boolean.TRUE);
    when(define.getControlledFields()).thenReturn(Collections.<AccessControlDefine.Field>emptyList());

    var generator = new FieldControlSettingSchemaGenerator();
    assertTrue(generator.supports(define));
    assertEquals(1, generator.generate(define).size());
  }

  @Test
  void generatorsShouldNotSupportBlankNamesOrFalseFlags() {
    var define = mock(AccessControlDefine.class);
    var filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn(" ");
    when(define.getCanControlFields()).thenReturn(Boolean.FALSE);

    assertFalse(new CompanyFilterSettingSchemaGenerator().supports(define, filter));
    assertFalse(new DepartmentFilterSettingSchemaGenerator().supports(define, filter));
    assertFalse(new EmployeeFilterSettingSchemaGenerator().supports(define, filter));
    assertFalse(new FieldControlSettingSchemaGenerator().supports(define));
  }
}
