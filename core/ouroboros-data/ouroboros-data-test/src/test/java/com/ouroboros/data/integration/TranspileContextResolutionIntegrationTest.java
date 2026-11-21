package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.EnhancedDataModelProxy;
import com.ouroboros.data.transpile.TranspileContext;
import com.querydsl.core.types.Path;

@DisplayName("转译上下文字段解析测试")
class TranspileContextResolutionIntegrationTest extends AbstractDataIntegrationTest {

  @Test
  @DisplayName("主模型字段解析应返回数据库 rawName")
  void resolve_shouldUseFieldRawName() throws Exception {
    DataModel userModel = user();
    Field coreDataModelField = EnhancedDataModelProxy.class.getDeclaredField("coreDataModel");
    coreDataModelField.setAccessible(true);
    Object coreDataModel = coreDataModelField.get(userModel);

    Method createTranspileContext = coreDataModel.getClass().getDeclaredMethod("createTranspileContext");
    createTranspileContext.setAccessible(true);
    TranspileContext context = (TranspileContext) createTranspileContext.invoke(coreDataModel);

    Path<?> field = context.resolve("User", "departmentId")
        .orElseThrow(() -> new AssertionError("departmentId path should exist"));
    assertEquals("department_id", field.getMetadata().getName());
  }
}
