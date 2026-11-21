package com.ouroboros.webapi.extractor;

import java.lang.annotation.Annotation;

import jakarta.validation.constraints.*;

import com.ouroboros.webapi.openapi.SchemaModel;

class ValidationSchemaCustomizer {

  public SchemaModel apply(SchemaModel schema, Annotation[] annotations) {
    SchemaModel current = schema;
    for (Annotation annotation : annotations) {
      if (annotation instanceof NotBlank) {
        SchemaModel snapshot = current;
        current = current.map(builder -> {
          builder.nullable(false);
          builder.minLength(max(snapshot.getMinLength(), 1));
        });
        continue;
      }
      if (annotation instanceof NotEmpty) {
        SchemaModel snapshot = current;
        current = current.map(builder -> {
          builder.nullable(false);
          String type = snapshot.getType();
          if ("array".equals(type)) {
            builder.minItems(max(snapshot.getMinItems(), 1));
          } else if ("object".equals(type)) {
            builder.minProperties(max(snapshot.getMinProperties(), 1));
          } else {
            builder.minLength(max(snapshot.getMinLength(), 1));
          }
        });
        continue;
      }
      if (annotation instanceof NotNull) {
        current = current.map(builder -> builder.nullable(false));
        continue;
      }
      if (annotation instanceof Size) {
        Size size = (Size) annotation;
        SchemaModel snapshot = current;
        current = current.map(builder -> {
          String type = snapshot.getType();
          if ("array".equals(type)) {
            builder.minItems(size.min());
            if (size.max() < Integer.MAX_VALUE) {
              builder.maxItems(size.max());
            }
          } else if ("object".equals(type)) {
            builder.minProperties(size.min());
            if (size.max() < Integer.MAX_VALUE) {
              builder.maxProperties(size.max());
            }
          } else {
            builder.minLength(size.min());
            if (size.max() < Integer.MAX_VALUE) {
              builder.maxLength(size.max());
            }
          }
        });
        continue;
      }
      if (annotation instanceof Min) {
        Min min = (Min) annotation;
        current = current.map(builder -> builder.minimum(min.value()));
        continue;
      }
      if (annotation instanceof Max) {
        Max max = (Max) annotation;
        current = current.map(builder -> builder.maximum(max.value()));
        continue;
      }
      if (annotation instanceof DecimalMin) {
        DecimalMin min = (DecimalMin) annotation;
        current = current.map(builder -> {
          builder.minimum(decimalValue(min.value()));
          if (min.inclusive()) {
            return;
          }
          builder.exclusiveMinimum(decimalValue(min.value()));
        });
        continue;
      }
      if (annotation instanceof DecimalMax) {
        DecimalMax max = (DecimalMax) annotation;
        current = current.map(builder -> {
          builder.maximum(decimalValue(max.value()));
          if (max.inclusive()) {
            return;
          }
          builder.exclusiveMaximum(decimalValue(max.value()));
        });
        continue;
      }
      if (annotation instanceof Pattern) {
        Pattern pattern = (Pattern) annotation;
        current = current.map(builder -> builder.pattern(pattern.regexp()));
        continue;
      }
      if (annotation instanceof Email) {
        current = current.map(builder -> builder.format("email"));
      }
    }
    return current;
  }

  public boolean isRequired(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation instanceof NotNull || annotation instanceof NotBlank || annotation instanceof NotEmpty) {
        return true;
      }
    }
    return false;
  }

  private int max(Integer current, int minimum) {
    return current == null ? minimum : Math.max(current, minimum);
  }

  private Number decimalValue(String value) {
    try {
      return Double.valueOf(value);
    } catch (NumberFormatException parseFailure) {
      return null;
    }
  }
}
