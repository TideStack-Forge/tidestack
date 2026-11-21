package com.ouroboros.businessmodel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Code-first declaration of one Action contract and its handler binding. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessAction {
  String modelFullName();

  String name();

  String handlerName();

  String title() default "";

  String description() default "";

  String inputSchema() default "{\"type\":\"object\"}";

  String outputSchema() default "{\"type\":\"object\"}";

  boolean isEnabled() default true;

  boolean readOnly() default false;

  /** The same optional JSON predicate definition used by dynamic Actions. */
  String precondition() default "";

  /** JSON array of modelFullName/eventName references allowed from this Action. */
  String emits() default "[]";
}
