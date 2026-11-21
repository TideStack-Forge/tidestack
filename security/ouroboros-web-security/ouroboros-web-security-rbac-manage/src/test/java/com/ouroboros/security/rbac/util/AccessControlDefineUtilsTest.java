package com.ouroboros.security.rbac.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.security.authority.accesscontrol.setting.ACFilterSettingSchemaGenerator;
import com.ouroboros.security.authority.accesscontrol.setting.ACSettingSchemaGenerator;
import com.ouroboros.uischema.schema.SchemaNode;

class AccessControlDefineUtilsTest {

  @AfterEach
  void resetGenerators() {
    AccessControlDefineUtils.resetGeneratorsForTest();
  }

  @Test
  void shouldReturnEmptyWhenAccessControlDefineIsEmpty() {
    var authority = mock(Authority.class);
    when(authority.getAccessControlDefine()).thenReturn(AccessControlDefine.empty());

    assertTrue(AccessControlDefineUtils.generateAccessControlSettingSchema(authority).isEmpty());
  }

  @Test
  void shouldMergeFieldAndFilterSchemasWhenGeneratorsSupportDefinitions() {
    var define = mock(AccessControlDefine.class);
    var filterDefine = mock(AccessControlFilterDefine.class);
    when(define.getFilterDefine()).thenReturn(Collections.singletonList(filterDefine));

    var authority = mock(Authority.class);
    when(authority.getAccessControlDefine()).thenReturn(define);

    var fieldSchema = mock(SchemaNode.class);
    var filterSchema = mock(SchemaNode.class);

    var acSettingSchemaGenerator = mock(ACSettingSchemaGenerator.class);
    when(acSettingSchemaGenerator.supports(define)).thenReturn(true);
    when(acSettingSchemaGenerator.generate(define)).thenReturn(Collections.singletonList(fieldSchema));

    var acFilterSettingSchemaGenerator = mock(ACFilterSettingSchemaGenerator.class);
    when(acFilterSettingSchemaGenerator.supports(define, filterDefine)).thenReturn(true);
    when(acFilterSettingSchemaGenerator.generate(define, filterDefine)).thenReturn(Collections.singletonList(filterSchema));

    AccessControlDefineUtils.overrideGeneratorsForTest(
        Collections.singletonList(acSettingSchemaGenerator),
        Collections.singletonList(acFilterSettingSchemaGenerator)
    );

    var result = AccessControlDefineUtils.generateAccessControlSettingSchema(authority);

    assertEquals(2, result.size());
    assertEquals(Arrays.asList(fieldSchema, filterSchema), result);
  }

  @Test
  void shouldIgnoreUnsupportedAndEmptyFilterSchemas() {
    var define = mock(AccessControlDefine.class);
    var filterDefine = mock(AccessControlFilterDefine.class);
    when(define.getFilterDefine()).thenReturn(Collections.singletonList(filterDefine));

    var authority = mock(Authority.class);
    when(authority.getAccessControlDefine()).thenReturn(define);

    var acSettingSchemaGenerator = mock(ACSettingSchemaGenerator.class);
    when(acSettingSchemaGenerator.supports(define)).thenReturn(false);

    var acFilterSettingSchemaGenerator = mock(ACFilterSettingSchemaGenerator.class);
    when(acFilterSettingSchemaGenerator.supports(define, filterDefine)).thenReturn(false);

    AccessControlDefineUtils.overrideGeneratorsForTest(
        Collections.singletonList(acSettingSchemaGenerator),
        Collections.singletonList(acFilterSettingSchemaGenerator)
    );

    var result = AccessControlDefineUtils.generateAccessControlSettingSchema(authority);
    assertTrue(result.isEmpty());
  }
}
