package com.ouroboros.webapi.extractor;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.multipart.MultipartFile;

import com.ouroboros.webapi.openapi.SchemaModel;

class JavaTypeToSchemaMapper {
  private final ValidationSchemaCustomizer validationSchemaCustomizer;
  private final SwaggerAnnotationExtractor swaggerAnnotationExtractor;

  public JavaTypeToSchemaMapper(ValidationSchemaCustomizer validationSchemaCustomizer, SwaggerAnnotationExtractor swaggerAnnotationExtractor) {
    this.validationSchemaCustomizer = validationSchemaCustomizer;
    this.swaggerAnnotationExtractor = swaggerAnnotationExtractor;
  }

  public SchemaModel fromParameter(Parameter parameter) {
    SchemaModel schema = fromType(parameter.getParameterizedType(), new HashSet<Class<?>>());
    schema = validationSchemaCustomizer.apply(schema, parameter.getAnnotations());
    return swaggerAnnotationExtractor.applySchema(schema, parameter);
  }

  public SchemaModel fromType(Type type) {
    return fromType(type, new HashSet<Class<?>>());
  }

  private SchemaModel fromType(Type type, Set<Class<?>> visiting) {
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Type rawType = parameterizedType.getRawType();
      if (rawType instanceof Class<?>) {
        Class<?> rawClass = (Class<?>) rawType;
        if (Collection.class.isAssignableFrom(rawClass)) {
          return SchemaModel.builder()
              .arrayType()
              .items(fromType(parameterizedType.getActualTypeArguments()[0], visiting))
              .build();
        }
        if (Map.class.isAssignableFrom(rawClass)) {
          Type[] typeArguments = parameterizedType.getActualTypeArguments();
          Type valueType = typeArguments.length > 1 ? typeArguments[1] : Object.class;
          return SchemaModel.builder()
              .objectType()
              .additionalProperties(fromType(valueType, visiting))
              .build();
        }
        return fromClass(rawClass, visiting);
      }
    }

    if (type instanceof GenericArrayType) {
      GenericArrayType genericArrayType = (GenericArrayType) type;
      return SchemaModel.builder()
          .arrayType()
          .items(fromType(genericArrayType.getGenericComponentType(), visiting))
          .build();
    }

    if (type instanceof Class<?>) {
      Class<?> clazz = (Class<?>) type;
      return fromClass(clazz, visiting);
    }

    return SchemaModel.builder().objectType().build();
  }

  private SchemaModel fromClass(Class<?> clazz, Set<Class<?>> visiting) {
    if (clazz == String.class || clazz == Character.class || clazz == char.class) {
      return SchemaModel.builder().stringType().build();
    }
    if (clazz == Boolean.class || clazz == boolean.class) {
      return SchemaModel.builder().booleanType().build();
    }
    if (isIntegerType(clazz)) {
      return SchemaModel.builder().integerType().build();
    }
    if (isNumberType(clazz)) {
      return SchemaModel.builder().numberType().build();
    }
    if (clazz.isEnum()) {
      List<Object> enumValues = Arrays.stream(clazz.getEnumConstants())
          .map(String::valueOf)
          .collect(Collectors.toList());
      return SchemaModel.builder().stringType().enumValues(enumValues).build();
    }
    if (MultipartFile.class.isAssignableFrom(clazz)) {
      return SchemaModel.builder()
          .stringType()
          .format("binary")
          .build();
    }
    if (clazz.isArray()) {
      return SchemaModel.builder()
          .arrayType()
          .items(fromClass(clazz.getComponentType(), visiting))
          .build();
    }
    if (Collection.class.isAssignableFrom(clazz)) {
      return SchemaModel.builder().arrayType().build();
    }
    if (Map.class.isAssignableFrom(clazz)) {
      return SchemaModel.builder().objectType().additionalProperties(Boolean.TRUE).build();
    }
    if (clazz == Void.class || clazz == void.class) {
      return null;
    }
    if (clazz.getName().startsWith("java.")) {
      return SchemaModel.builder().objectType().build();
    }
    if (visiting.contains(clazz)) {
      return SchemaModel.builder().objectType().build();
    }

    visiting.add(clazz);
    try {
      Map<String, SchemaModel> properties = new LinkedHashMap<>();
      List<String> required = new ArrayList<>();

      for (Field field : clazz.getDeclaredFields()) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        SchemaModel fieldSchema = fromType(field.getGenericType(), visiting);
        fieldSchema = validationSchemaCustomizer.apply(fieldSchema, field.getAnnotations());
        fieldSchema = swaggerAnnotationExtractor.applySchema(fieldSchema, field);
        properties.put(field.getName(), fieldSchema);

        if (isRequired(field.getAnnotations()) || swaggerAnnotationExtractor.isRequired(field)) {
          required.add(field.getName());
        }
      }

      SchemaModel.Builder builder = SchemaModel.builder()
          .objectType()
          .properties(properties);
      if (!required.isEmpty()) {
        builder.required(required);
      }
      return swaggerAnnotationExtractor.applySchema(builder.build(), clazz);
    } finally {
      visiting.remove(clazz);
    }
  }

  private boolean isRequired(Annotation[] annotations) {
    return validationSchemaCustomizer.isRequired(annotations);
  }

  private boolean isIntegerType(Class<?> clazz) {
    return clazz == Integer.class || clazz == int.class
        || clazz == Long.class || clazz == long.class
        || clazz == Short.class || clazz == short.class
        || clazz == Byte.class || clazz == byte.class
        || clazz == BigInteger.class;
  }

  private boolean isNumberType(Class<?> clazz) {
    return clazz == Float.class || clazz == float.class
        || clazz == Double.class || clazz == double.class
        || clazz == BigDecimal.class;
  }
}
