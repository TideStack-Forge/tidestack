package com.ouroboros.organizationstructure.service.impl;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.organizationstructure.entity.PositionEntity;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class PositionManageServiceImplDefaultPositionTest {

  @Test
  void getOrCreateDefaultPositionShouldReuseLegacyRowsWithNullPositionKey() throws Exception {
    PositionManageServiceImpl service = spy(new PositionManageServiceImpl());
    ReflectionTestUtils.setField(service, "self", service);

    PositionEntity legacyDefault = new PositionEntity();
    legacyDefault.setId("position-1");
    legacyDefault.setParent("department-1");
    legacyDefault.setPositionName("成员");
    legacyDefault.setPositionKey(null);
    doReturn(Collections.singletonList(legacyDefault)).when(service).doFindAll();

    PositionEntity result = service.getOrCreateDefaultPosition("department-1");

    assertSame(legacyDefault, result);
    verify(service, never()).assignDefinitionToDepartment(anyString(), anyString(), anyString(), any(), any());
  }
}
