package com.ouroboros.businessmodel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Associates a typed payload DTO with an existing dynamic Event identity. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BusinessEventPayload {
  String modelFullName();

  String eventName();

  String schemaFingerprint() default "";
}
