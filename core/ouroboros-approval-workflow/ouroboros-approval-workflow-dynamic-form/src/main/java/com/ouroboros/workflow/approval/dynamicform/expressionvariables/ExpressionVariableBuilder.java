package com.ouroboros.workflow.approval.dynamicform.expressionvariables;

import com.ouroboros.dynamicform.model.FormField;
import com.ouroboros.workflow.approval.model.ExpressionVariable;

/**
 * @author liansz
 **/
public interface ExpressionVariableBuilder {

  boolean support(FormField field);

  ExpressionVariable build(FormField field);
}
