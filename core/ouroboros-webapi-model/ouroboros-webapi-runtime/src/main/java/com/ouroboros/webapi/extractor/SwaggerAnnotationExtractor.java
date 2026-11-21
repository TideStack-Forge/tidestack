package com.ouroboros.webapi.extractor;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.openapi.ExternalDocsModel;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;
import com.ouroboros.webapi.openapi.SecurityRequirementModel;

class SwaggerAnnotationExtractor {
  private static final String DEFAULT_JSON_CONTENT_TYPE = "application/json";

  private JavaTypeToSchemaMapper schemaMapper;

  void setSchemaMapper(JavaTypeToSchemaMapper schemaMapper) {
    this.schemaMapper = schemaMapper;
  }

  public void applyOperation(SimpleWebAPIModel.Builder builder, Method method) {
    Operation operation = method.getAnnotation(Operation.class);
    var tags = method.getAnnotationsByType(io.swagger.v3.oas.annotations.tags.Tag.class);
    if (tags.length == 0) tags = method.getDeclaringClass().getAnnotationsByType(io.swagger.v3.oas.annotations.tags.Tag.class);
    if (tags.length > 0) builder.tags(Arrays.stream(tags).map(io.swagger.v3.oas.annotations.tags.Tag::name).filter(this::hasText).toList());
    var security = method.getAnnotationsByType(SecurityRequirement.class);
    if (security.length == 0) security = method.getDeclaringClass().getAnnotationsByType(SecurityRequirement.class);
    if (operation != null && operation.security().length > 0) security = operation.security();
    for (SecurityRequirement requirement : security) {
      if (hasText(requirement.name())) {
        builder.securityRequirement(SecurityRequirementModel.of(requirement.name(), Arrays.asList(requirement.scopes())));
      }
    }
    if (operation == null) return;

    if (hasText(operation.summary())) {
      builder.summary(operation.summary());
    }
    if (hasText(operation.description())) {
      builder.description(operation.description());
    }
    if (hasText(operation.operationId())) {
      builder.operationId(operation.operationId());
    }
    if (operation.tags().length > 0) {
      builder.tags(Arrays.asList(operation.tags()));
    }
    if (operation.deprecated()) {
      builder.deprecated(true);
    }

    ExternalDocumentation docs = operation.externalDocs();
    if (docs != null && (hasText(docs.url()) || hasText(docs.description()))) {
      builder.externalDocs(ExternalDocsModel.builder()
          .url(docs.url())
          .description(docs.description())
          .build());
    }

  }

  public ParameterModel applyParameter(ParameterModel model, Parameter parameter) {
    io.swagger.v3.oas.annotations.Parameter annotation = parameter.getAnnotation(io.swagger.v3.oas.annotations.Parameter.class);
    if (annotation == null) {
      return model;
    }

    ParameterModel current = model;
    if (hasText(annotation.name())) {
      current = current.map(builder -> builder.name(annotation.name()));
    }
    if (hasText(annotation.description())) {
      current = current.map(builder -> builder.description(annotation.description()));
    }
    if (annotation.required()) {
      current = current.map(builder -> builder.required(true));
    }
    if (annotation.deprecated()) {
      current = current.map(builder -> builder.deprecated(true));
    }
    if (hasText(annotation.example())) {
      current = current.map(builder -> builder.example(annotation.example()));
    }

    SchemaModel schema = applySchemaAnnotation(current.getSchema(), annotation.schema());
    if (schema != current.getSchema()) {
      SchemaModel finalSchema = schema;
      current = current.map(builder -> builder.schema(finalSchema));
    }
    return current;
  }

  public RequestBodyModel applyRequestBody(RequestBodyModel model, Parameter parameter) {
    io.swagger.v3.oas.annotations.parameters.RequestBody annotation =
        parameter.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);
    if (annotation == null) {
      return model;
    }

    RequestBodyModel current = model;
    if (hasText(annotation.description())) {
      current = current.map(builder -> builder.description(annotation.description()));
    }
    if (annotation.required()) {
      current = current.map(builder -> builder.required(true));
    }
    if (annotation.content().length > 0) {
      Map<String, MediaTypeModel> mergedContent = mergeContent(current.getContent(), annotation.content());
      current = current.map(builder -> builder.content(mergedContent));
    }
    return current;
  }

  public Map<String, ResponseModel> extractResponses(Method method) {
    Map<String, ResponseModel> responses = new LinkedHashMap<>();
    for (AnnotatedElement source : new AnnotatedElement[]{method.getDeclaringClass(), method}) {
      ApiResponses container = source.getAnnotation(ApiResponses.class);
      if (container != null) {
        for (ApiResponse response : container.value()) {
          responses.put(response.responseCode(), toResponseModel(response));
        }
      }
      ApiResponse single = source.getAnnotation(ApiResponse.class);
      if (single != null) responses.put(single.responseCode(), toResponseModel(single));
    }
    return responses;
  }

  public SchemaModel applySchema(SchemaModel schema, AnnotatedElement element) {
    io.swagger.v3.oas.annotations.media.Schema annotation = element.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
    return applySchemaAnnotation(schema, annotation);
  }

  public boolean isRequired(AnnotatedElement element) {
    io.swagger.v3.oas.annotations.media.Schema annotation = element.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
    return annotation != null && annotation.requiredMode() == RequiredMode.REQUIRED;
  }

  private ResponseModel toResponseModel(ApiResponse response) {
    ResponseModel.Builder builder = ResponseModel.builder().description(response.description());
    if (response.content().length > 0) {
      builder.content(extractContent(response.content()));
    }
    return builder.build();
  }

  private Map<String, MediaTypeModel> extractContent(Content[] content) {
    return Arrays.stream(content)
        .collect(Collectors.toMap(
            this::resolveMediaType,
            this::toMediaTypeModel,
            (left, right) -> right,
            LinkedHashMap::new
        ));
  }

  private Map<String, MediaTypeModel> mergeContent(Map<String, MediaTypeModel> existing, Content[] content) {
    Map<String, MediaTypeModel> merged = existing != null
        ? new LinkedHashMap<>(existing)
        : new LinkedHashMap<>();
    Map<String, MediaTypeModel> annotated = extractContent(content);
    for (Map.Entry<String, MediaTypeModel> entry : annotated.entrySet()) {
      MediaTypeModel existingMediaType = merged.get(entry.getKey());
      if (existingMediaType == null) {
        merged.put(entry.getKey(), entry.getValue());
        continue;
      }

      MediaTypeModel annotatedMediaType = entry.getValue();
      merged.put(entry.getKey(), existingMediaType.map(builder -> {
        if (hasMeaningfulSchema(annotatedMediaType.getSchema())) {
          builder.schema(annotatedMediaType.getSchema());
        }
        if (annotatedMediaType.getExample() != null) {
          builder.example(annotatedMediaType.getExample());
        }
        if (annotatedMediaType.getExamples() != null && !annotatedMediaType.getExamples().isEmpty()) {
          builder.examples(annotatedMediaType.getExamples());
        }
        if (annotatedMediaType.getEncoding() != null && !annotatedMediaType.getEncoding().isEmpty()) {
          builder.encoding(annotatedMediaType.getEncoding());
        }
      }));
    }
    return merged;
  }

  private SchemaModel applySchemaAnnotation(SchemaModel schema, io.swagger.v3.oas.annotations.media.Schema annotation) {
    if (annotation == null) {
      return schema;
    }

    SchemaModel current = schema;
    if (annotation.implementation() != null && annotation.implementation() != Void.class && schemaMapper != null) {
      current = schemaMapper.fromType(annotation.implementation());
    }
    if (current == null) {
      current = SchemaModel.builder().build();
    }

    if (hasText(annotation.description())) {
      current = current.map(builder -> builder.description(annotation.description()));
    }
    if (hasText(annotation.title())) {
      current = current.map(builder -> builder.title(annotation.title()));
    }
    if (hasText(annotation.format())) {
      current = current.map(builder -> builder.format(annotation.format()));
    }
    if (hasText(annotation.type())) {
      current = current.map(builder -> builder.type(annotation.type()));
    }
    if (hasSchemaStringValue(annotation.defaultValue())) {
      current = current.map(builder -> builder.defaultValue(annotation.defaultValue()));
    }
    if (hasText(annotation.example())) {
      current = current.map(builder -> builder.example(annotation.example()));
    }
    if (annotation.allowableValues().length > 0) {
      List<Object> enumValues = Arrays.stream(annotation.allowableValues())
          .map(value -> (Object) value)
          .collect(Collectors.toList());
      current = current.map(builder -> builder.enumValues(enumValues));
    }
    var minimum = parseDecimal(annotation.minimum());
    if (minimum.isPresent()) {
      current = current.map(builder -> builder.minimum(minimum.get()));
    }
    var maximum = parseDecimal(annotation.maximum());
    if (maximum.isPresent()) {
      current = current.map(builder -> builder.maximum(maximum.get()));
    }
    if (annotation.minLength() > 0) {
      current = current.map(builder -> builder.minLength(annotation.minLength()));
    }
    if (annotation.maxLength() > 0 && annotation.maxLength() != Integer.MAX_VALUE) {
      current = current.map(builder -> builder.maxLength(annotation.maxLength()));
    }
    if (hasText(annotation.pattern())) {
      current = current.map(builder -> builder.pattern(annotation.pattern()));
    }
    if (annotation.nullable()) {
      current = current.map(builder -> builder.nullable(true));
    }
    if (annotation.deprecated()) {
      current = current.map(builder -> builder.deprecated(true));
    }
    if (annotation.accessMode() == io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY) {
      current = current.map(builder -> builder.readOnly(true));
    }
    if (annotation.accessMode() == io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY) {
      current = current.map(builder -> builder.writeOnly(true));
    }
    return current;
  }

  private MediaTypeModel toMediaTypeModel(Content item) {
    MediaTypeModel.Builder builder = MediaTypeModel.builder();
    SchemaModel schema = toSchemaModel(item.schema());
    if (hasMeaningfulSchema(schema)) {
      builder.schema(schema);
    }
    return builder.build();
  }

  private SchemaModel toSchemaModel(io.swagger.v3.oas.annotations.media.Schema annotation) {
    if (!hasSchemaMetadata(annotation)) {
      return null;
    }
    return applySchemaAnnotation(null, annotation);
  }

  private boolean hasSchemaMetadata(io.swagger.v3.oas.annotations.media.Schema annotation) {
    return annotation != null && (
        (annotation.implementation() != null && annotation.implementation() != Void.class)
            || hasText(annotation.title())
            || hasText(annotation.description())
            || hasText(annotation.format())
            || hasText(annotation.type())
            || hasSchemaStringValue(annotation.defaultValue())
            || hasText(annotation.example())
            || hasText(annotation.minimum())
            || hasText(annotation.maximum())
            || hasText(annotation.pattern())
            || annotation.allowableValues().length > 0
            || annotation.nullable()
            || annotation.deprecated()
            || annotation.minLength() > 0
            || (annotation.maxLength() > 0 && annotation.maxLength() != Integer.MAX_VALUE)
            || annotation.accessMode() != io.swagger.v3.oas.annotations.media.Schema.AccessMode.AUTO
    );
  }

  private String resolveMediaType(Content item) {
    return hasText(item.mediaType()) ? item.mediaType() : DEFAULT_JSON_CONTENT_TYPE;
  }

  private java.util.Optional<BigDecimal> parseDecimal(String value) {
    if (!hasText(value)) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.of(new BigDecimal(value));
    } catch (NumberFormatException parseFailure) {
      return java.util.Optional.empty();
    }
  }

  private boolean hasMeaningfulSchema(SchemaModel schema) {
    return schema != null && (
        schema.getType() != null
            || (schema.getTypes() != null && !schema.getTypes().isEmpty())
            || hasText(schema.getTitle())
            || hasText(schema.getDescription())
            || hasText(schema.getFormat())
            || schema.getDefaultValue() != null
            || schema.getNullable() != null
            || schema.getReadOnly() != null
            || schema.getWriteOnly() != null
            || schema.getExample() != null
            || schema.getDeprecated() != null
            || schema.getEnumValues() != null && !schema.getEnumValues().isEmpty()
            || schema.getProperties() != null && !schema.getProperties().isEmpty()
            || schema.getRequired() != null && !schema.getRequired().isEmpty()
            || schema.getItems() != null
            || schema.getMinimum() != null
            || schema.getMaximum() != null
            || schema.getMinLength() != null
            || schema.getMaxLength() != null
            || hasText(schema.getPattern())
            || schema.getMinItems() != null
            || schema.getMaxItems() != null
            || schema.getUniqueItems() != null
            || schema.getOneOf() != null && !schema.getOneOf().isEmpty()
            || schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()
            || schema.getAllOf() != null && !schema.getAllOf().isEmpty()
            || schema.getNot() != null
            || schema.getAdditionalProperties() != null
            || schema.getMultipleOf() != null
            || schema.getExclusiveMinimum() != null
            || schema.getExclusiveMaximum() != null
            || schema.getMinProperties() != null
            || schema.getMaxProperties() != null
            || schema.getConstValue() != null
            || hasText(schema.getRef())
            || !schema.getExtraProps().isEmpty()
    );
  }

  private boolean hasSchemaStringValue(String value) {
    return hasText(value);
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
