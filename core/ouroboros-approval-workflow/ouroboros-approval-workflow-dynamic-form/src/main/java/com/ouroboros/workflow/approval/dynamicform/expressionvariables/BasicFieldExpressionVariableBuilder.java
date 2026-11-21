package com.ouroboros.workflow.approval.dynamicform.expressionvariables;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.workflow.approval.form.ApprovalFormFieldProjector;
import com.ouroboros.workflow.approval.model.ExpressionVariable;

public class BasicFieldExpressionVariableBuilder implements ExpressionVariableBuilder {
  @Override
  public boolean support(FormField field) {
    return projector().map(value -> value.supportsCondition(field.getType())).orElse(false);
  }

  @Override
  public ExpressionVariable build(FormField field) {
    var projector = projector().orElseThrow(() -> new InternalException("字段类型目录不可用"));
    return projector.condition(field.getName(), field.getLabel(), field.getType(),
        projector.compile(field.getType(), field.getParams() == null ? Map.of() : field.getParams()))
        .orElseThrow(() -> new BusinessException("此字段不支持审批条件: " + field.getName()));
  }

  private Optional<ApprovalFormFieldProjector> projector() {
    return Optional.ofNullable(SpringContextUtil.getApplicationContext())
        .map(context -> new ApprovalFormFieldProjector(SpringContextUtil.getBean(FieldTypeCatalog.class), FieldTypeCatalogContext.runtime()));
  }
}
