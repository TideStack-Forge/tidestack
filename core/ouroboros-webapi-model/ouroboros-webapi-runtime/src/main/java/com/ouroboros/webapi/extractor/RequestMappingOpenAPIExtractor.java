package com.ouroboros.webapi.extractor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.servlet.mvc.condition.ProducesRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;

public class RequestMappingOpenAPIExtractor {
  private final SwaggerAnnotationExtractor swaggerAnnotationExtractor;
  private final JavaTypeToSchemaMapper schemaMapper;
  private final SpringMvcParameterExtractor springMvcParameterExtractor;
  private final StandardWebResultSchemaMapper standardWebResultSchemaMapper;

  public RequestMappingOpenAPIExtractor() {
    this.swaggerAnnotationExtractor = new SwaggerAnnotationExtractor();
    ValidationSchemaCustomizer validationSchemaCustomizer = new ValidationSchemaCustomizer();
    this.schemaMapper = new JavaTypeToSchemaMapper(validationSchemaCustomizer, swaggerAnnotationExtractor);
    this.swaggerAnnotationExtractor.setSchemaMapper(schemaMapper);
    this.springMvcParameterExtractor = new SpringMvcParameterExtractor(schemaMapper, swaggerAnnotationExtractor);
    this.standardWebResultSchemaMapper = new StandardWebResultSchemaMapper(schemaMapper);
  }

  public SimpleWebAPIModel.Builder extract(
      SimpleWebAPIModel.Builder builder,
      RequestMappingInfo requestMappingInfo,
      Class<?> handlerType,
      Method method
  ) {
    SpringMvcParameterExtractor.ExtractionResult extractionResult = springMvcParameterExtractor.extract(method, requestMappingInfo);
    extractionResult.getParameters().forEach(builder::parameter);
    if (extractionResult.getRequestBody() != null) {
      builder.requestBody(extractionResult.getRequestBody());
    }

    swaggerAnnotationExtractor.applyOperation(builder, method);

    Map<String, ResponseModel> responses = swaggerAnnotationExtractor.extractResponses(method);
    if (method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class) == null
        && method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse.class) == null) {
      var inferred = defaultResponses(requestMappingInfo, method);
      inferred.putAll(responses);
      responses = inferred;
    }
    responses.forEach(builder::response);
    return builder;
  }

  private Map<String, ResponseModel> defaultResponses(RequestMappingInfo requestMappingInfo, Method method) {
    Map<String, ResponseModel> responses = new LinkedHashMap<>();
    ResponseModel.Builder responseBuilder = ResponseModel.builder().description(defaultDescription(method));

    SchemaModel responseSchema = standardWebResultSchemaMapper.fromReturnType(method.getGenericReturnType())
        .orElseGet(() -> schemaMapper.fromType(method.getGenericReturnType()));
    if (responseSchema != null) {
      Map<String, MediaTypeModel> content = new LinkedHashMap<>();
      for (String mediaType : resolveMediaTypes(requestMappingInfo.getProducesCondition())) {
        content.put(mediaType, MediaTypeModel.builder().schema(responseSchema).build());
      }
      responseBuilder.content(content);
    }

    responses.put(defaultStatusCode(method), responseBuilder.build());
    return responses;
  }

  private String defaultStatusCode(Method method) {
    return method.getReturnType() == Void.TYPE || method.getReturnType() == Void.class ? "204" : "200";
  }

  private String defaultDescription(Method method) {
    return method.getReturnType() == Void.TYPE || method.getReturnType() == Void.class ? "No Content" : "OK";
  }

  private Iterable<String> resolveMediaTypes(ProducesRequestCondition condition) {
    if (condition == null || condition.getProducibleMediaTypes().isEmpty()) {
      return Collections.singletonList("application/json");
    }
    return condition.getProducibleMediaTypes().stream()
        .map(Object::toString)
        .collect(Collectors.toList());
  }
}
