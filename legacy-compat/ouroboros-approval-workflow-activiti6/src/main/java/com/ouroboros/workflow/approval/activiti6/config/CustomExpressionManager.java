package com.ouroboros.workflow.approval.activiti6.config;

import javax.el.CompositeELResolver;

import org.activiti.engine.delegate.VariableScope;
import org.activiti.engine.impl.el.ActivitiElContext;
import org.activiti.engine.impl.el.ExpressionManager;

/**
 * @author liansz
 **/
public class CustomExpressionManager extends ExpressionManager {

  @Override
  protected ActivitiElContext createElContext(VariableScope variableScope) {
    CompositeELResolver elResolver = (CompositeELResolver) super.createElResolver(variableScope);
    elResolver.add(new NullSafeELResolver());
    return new ActivitiElContext(elResolver);
  }
}
