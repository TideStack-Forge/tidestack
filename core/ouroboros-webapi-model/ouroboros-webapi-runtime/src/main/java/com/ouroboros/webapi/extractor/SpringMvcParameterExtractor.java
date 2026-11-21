package com.ouroboros.webapi.extractor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.condition.ConsumesRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.SchemaModel;

class SpringMvcParameterExtractor {
  private final JavaTypeToSchemaMapper schemaMapper;
  private final SwaggerAnnotationExtractor swaggerAnnotationExtractor;
  private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

  public SpringMvcParameterExtractor(JavaTypeToSchemaMapper schemaMapper, SwaggerAnnotationExtractor swaggerAnnotationExtractor) {
    this.schemaMapper = schemaMapper;
    this.swaggerAnnotationExtractor = swaggerAnnotationExtractor;
  }

  public ExtractionResult extract(Method method, RequestMappingInfo requestMappingInfo) {
    List<ParameterModel> parameters = new ArrayList<>();
    RequestBodyModel requestBody = null;
    String[] discoveredNames = parameterNameDiscoverer.getParameterNames(method);

    Parameter[] methodParameters = method.getParameters();
    for (int index = 0; index < methodParameters.length; index++) {
      Parameter parameter = methodParameters[index];
      String discoveredName = discoveredNames != null && index < discoveredNames.length ? discoveredNames[index] : parameter.getName();

      if (isMultipartParameter(parameter)) {
        requestBody = mergeMultipartRequestBody(requestBody, parameter, discoveredName, requestMappingInfo);
        continue;
      }

      if (parameter.getAnnotation(RequestBody.class) != null || hasSwaggerRequestBody(parameter)) {
        requestBody = buildRequestBody(parameter, requestMappingInfo);
        continue;
      }

      ParameterModel parameterModel = buildParameter(parameter, discoveredName);
      if (parameterModel != null) {
        parameters.add(swaggerAnnotationExtractor.applyParameter(parameterModel, parameter));
      }
    }

    return new ExtractionResult(parameters, requestBody);
  }

  private ParameterModel buildParameter(Parameter parameter, String discoveredName) {
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    if (pathVariable != null) {
      return ParameterModel.builder()
          .name(resolveName(pathVariable.name(), pathVariable.value(), discoveredName))
          .in(ParameterLocation.PATH)
          .required(true)
          .schema(schemaMapper.fromParameter(parameter))
          .build();
    }

    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam != null) {
      boolean hasDefaultValue = !ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue());
      return ParameterModel.builder()
          .name(resolveName(requestParam.name(), requestParam.value(), discoveredName))
          .in(ParameterLocation.QUERY)
          .required(hasDefaultValue ? false : requestParam.required())
          .schema(schemaMapper.fromParameter(parameter))
          .build();
    }

    RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
    if (requestHeader != null) {
      boolean hasDefaultValue = !ValueConstants.DEFAULT_NONE.equals(requestHeader.defaultValue());
      return ParameterModel.builder()
          .name(resolveName(requestHeader.name(), requestHeader.value(), discoveredName))
          .in(ParameterLocation.HEADER)
          .required(hasDefaultValue ? false : requestHeader.required())
          .schema(schemaMapper.fromParameter(parameter))
          .build();
    }

    CookieValue cookieValue = parameter.getAnnotation(CookieValue.class);
    if (cookieValue != null) {
      boolean hasDefaultValue = !ValueConstants.DEFAULT_NONE.equals(cookieValue.defaultValue());
      return ParameterModel.builder()
          .name(resolveName(cookieValue.name(), cookieValue.value(), discoveredName))
          .in(ParameterLocation.COOKIE)
          .required(hasDefaultValue ? false : cookieValue.required())
          .schema(schemaMapper.fromParameter(parameter))
          .build();
    }

    if (isSimpleParameter(parameter.getType()) || isQueryParameterMap(parameter)) {
      return ParameterModel.builder()
          .name(discoveredName)
          .in(ParameterLocation.QUERY)
          .required(false)
          .schema(schemaMapper.fromParameter(parameter))
          .build();
    }

    return null;
  }

  private RequestBodyModel buildRequestBody(Parameter parameter, RequestMappingInfo requestMappingInfo) {
    Map<String, MediaTypeModel> content = new LinkedHashMap<>();
    for (String mediaType : resolveMediaTypes(
        requestMappingInfo.getConsumesCondition(),
        inferDefaultRequestBodyMediaType(parameter)
    )) {
      content.put(mediaType, MediaTypeModel.builder()
          .schema(schemaMapper.fromParameter(parameter))
          .build());
    }

    RequestBodyModel requestBody = RequestBodyModel.builder()
        .required(resolveRequestBodyRequired(parameter))
        .content(content)
        .build();

    return swaggerAnnotationExtractor.applyRequestBody(requestBody, parameter);
  }

  private RequestBodyModel mergeMultipartRequestBody(
      RequestBodyModel current,
      Parameter parameter,
      String discoveredName,
      RequestMappingInfo requestMappingInfo
  ) {
    String partName = resolveMultipartName(parameter, discoveredName);
    boolean required = resolveMultipartRequired(parameter);
    SchemaModel partSchema = applyMultipartMetadataToSchema(schemaMapper.fromParameter(parameter), parameter);

    Map<String, MediaTypeModel> content = current != null && current.getContent() != null
        ? new LinkedHashMap<>(current.getContent())
        : new LinkedHashMap<>();
    String mediaType = resolveMultipartMediaType(requestMappingInfo.getConsumesCondition());
    MediaTypeModel existingMediaType = content.get(mediaType);
    SchemaModel schema = existingMediaType != null ? existingMediaType.getSchema() : null;
    SchemaModel mergedSchema = mergeMultipartSchema(schema, partName, partSchema, required);
    content.put(mediaType, MediaTypeModel.builder()
        .schema(mergedSchema)
        .build());

    RequestBodyModel requestBody = current == null
        ? RequestBodyModel.builder()
            .content(content)
            .required(required)
            .build()
        : current.map(
            builder -> builder.content(content),
            builder -> builder.required(Boolean.TRUE.equals(current.getRequired()) || required)
        );
    return swaggerAnnotationExtractor.applyRequestBody(requestBody, parameter);
  }

  private SchemaModel mergeMultipartSchema(SchemaModel schema, String partName, SchemaModel partSchema, boolean required) {
    Map<String, SchemaModel> properties = schema != null && schema.getProperties() != null
        ? new LinkedHashMap<>(schema.getProperties())
        : new LinkedHashMap<>();
    properties.put(partName, partSchema);

    Set<String> requiredFields = new LinkedHashSet<>();
    if (schema != null && schema.getRequired() != null) {
      requiredFields.addAll(schema.getRequired());
    }
    if (required) {
      requiredFields.add(partName);
    }

    SchemaModel.Builder builder = SchemaModel.builder()
        .objectType()
        .properties(properties);
    if (!requiredFields.isEmpty()) {
      builder.required(new ArrayList<>(requiredFields));
    }
    return builder.build();
  }

  private SchemaModel applyMultipartMetadataToSchema(SchemaModel schema, Parameter parameter) {
    io.swagger.v3.oas.annotations.Parameter annotation =
        parameter.getAnnotation(io.swagger.v3.oas.annotations.Parameter.class);
    if (annotation == null || schema == null) {
      return schema;
    }

    SchemaModel current = schema;
    if (hasText(annotation.description())) {
      current = current.map(builder -> builder.description(annotation.description()));
    }
    if (annotation.deprecated()) {
      current = current.map(builder -> builder.deprecated(true));
    }
    if (hasText(annotation.example())) {
      current = current.map(builder -> builder.example(annotation.example()));
    }
    return current;
  }

  private List<String> resolveMediaTypes(ConsumesRequestCondition condition, String defaultMediaType) {
    if (condition == null || condition.getConsumableMediaTypes().isEmpty()) {
      return Collections.singletonList(defaultMediaType);
    }
    return condition.getConsumableMediaTypes().stream()
        .map(Object::toString)
        .collect(Collectors.toList());
  }

  private String inferDefaultRequestBodyMediaType(Parameter parameter) {
    if (parameter.getAnnotation(RequestBody.class) != null) {
      return "application/json";
    }
    return containsMultipartType(parameter.getParameterizedType())
        ? "multipart/form-data"
        : "application/x-www-form-urlencoded";
  }

  private String resolveMultipartMediaType(ConsumesRequestCondition condition) {
    return resolveMediaTypes(condition, "multipart/form-data").get(0);
  }

  private String resolveName(String primary, String secondary, String fallback) {
    return Optional.ofNullable(primary)
        .filter(value -> !value.isEmpty())
        .orElseGet(() -> Optional.ofNullable(secondary)
            .filter(value -> !value.isEmpty())
            .orElse(fallback));
  }

  private String resolveMultipartName(Parameter parameter, String discoveredName) {
    RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
    if (requestPart != null) {
      return resolveName(requestPart.name(), requestPart.value(), discoveredName);
    }

    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam != null) {
      return resolveName(requestParam.name(), requestParam.value(), discoveredName);
    }
    return discoveredName;
  }

  private boolean resolveMultipartRequired(Parameter parameter) {
    RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
    if (requestPart != null) {
      return requestPart.required();
    }

    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam != null) {
      return ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue()) && requestParam.required();
    }
    return false;
  }

  private boolean isMultipartParameter(Parameter parameter) {
    return parameter.getAnnotation(RequestPart.class) != null
        || parameter.getAnnotation(RequestParam.class) != null && containsMultipartType(parameter.getParameterizedType());
  }

  private boolean hasSwaggerRequestBody(Parameter parameter) {
    return parameter.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class) != null;
  }

  private boolean resolveRequestBodyRequired(Parameter parameter) {
    RequestBody springRequestBody = parameter.getAnnotation(RequestBody.class);
    if (springRequestBody != null) {
      return springRequestBody.required();
    }

    io.swagger.v3.oas.annotations.parameters.RequestBody swaggerRequestBody =
        parameter.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);
    return swaggerRequestBody != null && swaggerRequestBody.required();
  }

  private boolean isSimpleParameter(Class<?> type) {
    return type.isPrimitive()
        || type == String.class
        || Number.class.isAssignableFrom(type)
        || type == Boolean.class
        || type == Character.class
        || type.isEnum();
  }

  private boolean isQueryParameterMap(Parameter parameter) {
    return parameter.getAnnotation(RequestParam.class) != null
        && Map.class.isAssignableFrom(parameter.getType());
  }

  private boolean containsMultipartType(Type type) {
    Set<Class<?>> visiting = new LinkedHashSet<>();
    Deque<Type> pending = new ArrayDeque<>();
    pending.add(type);
    while (!pending.isEmpty()) {
      Type current = pending.removeFirst();
      if (current instanceof Class<?>) {
        Class<?> clazz = (Class<?>) current;
        if (MultipartFile.class.isAssignableFrom(clazz)) {
          return true;
        }
        if (clazz.isArray()) {
          pending.add(clazz.getComponentType());
          continue;
        }
        if (clazz.getName().startsWith("java.") || !visiting.add(clazz)) {
          continue;
        }
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
          if (!field.isSynthetic() && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            pending.add(field.getGenericType());
          }
        }
        continue;
      }

      if (current instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) current;
        pending.add(parameterizedType.getRawType());
        for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
          pending.add(typeArgument);
        }
      }
    }
    return false;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  static class ExtractionResult {
    private final List<ParameterModel> parameters;
    private final RequestBodyModel requestBody;

    ExtractionResult(List<ParameterModel> parameters, RequestBodyModel requestBody) {
      this.parameters = parameters;
      this.requestBody = requestBody;
    }

    public List<ParameterModel> getParameters() {
      return parameters;
    }

    public RequestBodyModel getRequestBody() {
      return requestBody;
    }
  }
}
