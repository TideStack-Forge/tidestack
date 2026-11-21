package com.ouroboros.businessmodel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Code-first contribution of a complete dynamic Event definition. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BusinessEvent {
  String modelFullName();

  String eventName();

  String title();

  String description();

  String occurrenceCondition();

  String subject() default "instance";

  /** JSON Schema or the fully-qualified name of a matching {@link BusinessEventPayload} type. */
  String payloadSchema() default "{\"type\":\"object\"}";

  String[] allowedProducerKinds() default {"action"};

  boolean requireUnfilteredModelRead() default false;
}
