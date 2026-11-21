package com.ouroboros.businessmodel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Contributes a consumer-owned Event subscription or binds its listener implementation. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface BusinessEventSubscription {
  /** Applies to a subscription definition, never to a binding of an existing definition. */
  boolean isEnabled() default true;

  String consumerModelFullName();

  String subscriptionName();

  String sourceEventModelFullName() default "";

  String sourceEventName() default "";

  String filter() default "";

  String targetActionModelFullName() default "";

  String targetActionName() default "";

  String inputMapping() default "{}";

  String listenerName() default "";

  /** Literal subscription configuration; expressions are interpreted only by the listener's declared mapping fields. */
  String listenerConfig() default "";

  /** Optional JSON Schema owned by a method listener; a named external listener declares its own Schema. */
  String listenerConfigSchema() default "";

  /** Optional dynamic-input Schema owned by a method listener. */
  String listenerInputSchema() default "";

  String title() default "";

  String description() default "";

  /** A method listener's adapter derives its transaction capability from this explicit declaration. */
  com.ouroboros.businessmodel.event.BusinessEventExecutionMode executionMode();
}
