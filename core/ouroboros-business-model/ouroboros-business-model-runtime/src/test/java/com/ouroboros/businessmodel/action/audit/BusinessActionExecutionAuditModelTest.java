package com.ouroboros.businessmodel.action.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;

class BusinessActionExecutionAuditModelTest {
  @Test
  void auditModelUsesTheTypedDataModelContractWithAnIdPrimaryKey() {
    DataModelMeta meta = TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.action.audit")
        .get("ouroboros.BusinessActionExecutionAudit");

    assertNotNull(meta);
    assertEquals("t_business_action_execution_audit", meta.getRawName());
    DataModelFieldMeta id = meta.getFields().stream()
        .filter(field -> "id".equals(field.getName()))
        .findFirst()
        .orElseThrow();
    assertTrue(id.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false));
    assertTrue(meta.getFields().stream().anyMatch(field -> "writeSetJson".equals(field.getName())));
    assertTrue(meta.getFields().stream().anyMatch(field -> "transactionResult".equals(field.getName())));
    assertTrue(meta.getFields().stream().anyMatch(field -> "failureType".equals(field.getName())));
    assertNotNull(meta.getFields().stream().filter(field -> "executionAuthorityName".equals(field.getName()))
        .findFirst().orElseThrow());
    var historical = new BusinessActionExecutionAuditModel();
    assertEquals(null, historical.getExecutionAuthorityName());
    historical.setExecutionAuthorityName("sales.Order.workflow");
    assertEquals("sales.Order.workflow", historical.getExecutionAuthorityName());
  }
}
