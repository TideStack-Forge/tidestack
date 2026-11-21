package com.ouroboros.businessmodel;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Async;

import com.ouroboros.businessmodel.action.BusinessActionHandler;
import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;
import com.ouroboros.businessmodel.event.BusinessEventListener;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;

/** Checks the completion contract shared by annotated methods and directly registered listeners. */
public final class BusinessBindingCapabilities {
  private BusinessBindingCapabilities() {
  }

  public static boolean isSynchronous(BusinessActionHandler handler) {
    try {
      return isSynchronous(handler, BusinessActionHandler.class.getMethod("handle", BusinessActionContext.class, com.ouroboros.businessmodel.action.BusinessActionInput.class));
    } catch (NoSuchMethodException error) {
      throw new IllegalStateException("BusinessActionHandler completion method is unavailable", error);
    }
  }

  public static boolean isSynchronous(BusinessEventListener listener) {
    try {
      Method method = BusinessEventListener.class.getMethod("onEvent",
          BusinessEventOccurrence.class, BusinessEventDeliveryContext.class);
      return isSynchronous(listener, method);
    } catch (NoSuchMethodException error) {
      throw new IllegalStateException("BusinessEventListener completion method is unavailable", error);
    }
  }

  public static boolean isSynchronous(Object bean, Method method) {
    return isSynchronous(bean, method, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static boolean isSynchronous(Object bean, Method method, Set<Object> visited) {
    if (!visited.add(bean) || (AopUtils.isAopProxy(bean) && !(bean instanceof Advised))) {
      return false;
    }
    Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
    Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
    if (AnnotatedElementUtils.findMergedAnnotation(targetMethod, Async.class) != null
        || AnnotatedElementUtils.findMergedAnnotation(targetClass, Async.class) != null) {
      return false;
    }
    if (bean instanceof Advised advised) {
      Class<?> advisedClass = AopUtils.getTargetClass(bean);
      Method invocationMethod = AopUtils.isCglibProxy(bean)
          ? AopUtils.getMostSpecificMethod(method, advisedClass)
          : AopUtils.selectInvocableMethod(method, bean.getClass());
      for (var advisor : advised.getAdvisors()) {
        if (advisor.getAdvice() instanceof AsyncExecutionInterceptor
            && (!(advisor instanceof PointcutAdvisor pointcut)
                || (pointcut.getPointcut().getClassFilter().matches(advisedClass)
                    && pointcut.getPointcut().getMethodMatcher().matches(invocationMethod, advisedClass)))) {
          return false;
        }
      }
      Object target = AopProxyUtils.getSingletonTarget(bean);
      // A dynamic target cannot provide a stable completion contract for this published binding.
      return target != null && isSynchronous(target, method, visited);
    }
    return true;
  }
}
