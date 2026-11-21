package com.ouroboros.workflow.approval.activiti6.config;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELResolver;

/**
 * @author liansz
 **/
public class NullSafeELResolver extends ELResolver {

  @Override
  public Object getValue(ELContext context, Object base, Object property) {
    // 如果找不到变量则返回 null
    context.setPropertyResolved(true);
    return null;
  }

  @Override
  public Class<?> getType(ELContext context, Object base, Object property) {
    return Object.class;
  }

  @Override
  public void setValue(ELContext context, Object base, Object property, Object value) {
    // No-op
  }

  @Override
  public boolean isReadOnly(ELContext context, Object base, Object property) {
    return true;
  }

  @Override
  public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
    return null;
  }

  @Override
  public Class<?> getCommonPropertyType(ELContext context, Object base) {
    return Object.class;
  }
}

