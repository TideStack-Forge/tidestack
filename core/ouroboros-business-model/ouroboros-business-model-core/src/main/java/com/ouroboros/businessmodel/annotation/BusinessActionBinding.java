package com.ouroboros.businessmodel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a method to an Action contract already declared in business-model metadata. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessActionBinding {
  String modelFullName();

  String actionName();

  String handlerName() default "";

  String flowName() default "";
}
