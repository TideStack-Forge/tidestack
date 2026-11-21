package com.ouroboros.webapi.extractor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.openapi.SchemaModel;

class StandardWebResultSchemaMapper {
  private final JavaTypeToSchemaMapper schemaMapper;

  public StandardWebResultSchemaMapper(JavaTypeToSchemaMapper schemaMapper) {
    this.schemaMapper = schemaMapper;
  }

  public Optional<SchemaModel> fromReturnType(Type returnType) {
    if (!(returnType instanceof ParameterizedType)) {
      return Optional.empty();
    }

    ParameterizedType parameterizedType = (ParameterizedType) returnType;
    if (!(parameterizedType.getRawType() instanceof Class<?>)) {
      return Optional.empty();
    }

    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    Type innerType = parameterizedType.getActualTypeArguments()[0];

    if (rawType == WebApiResult.class) {
      return Optional.of(wrapperSchema(schemaMapper.fromType(innerType)));
    }
    if (rawType == ItemsWebApiResult.class) {
      SchemaModel items = SchemaModel.builder()
          .arrayType()
          .items(schemaMapper.fromType(innerType))
          .build();
      SchemaModel data = SchemaModel.builder()
          .objectType()
          .properties(singletonProperties("items", items))
          .build();
      return Optional.of(wrapperSchema(data));
    }
    if (rawType == PagedWebApiResult.class) {
      Map<String, SchemaModel> properties = new LinkedHashMap<>();
      properties.put("total", SchemaModel.builder().integerType().build());
      properties.put("items", SchemaModel.builder()
          .arrayType()
          .items(schemaMapper.fromType(innerType))
          .build());
      return Optional.of(wrapperSchema(SchemaModel.builder().objectType().properties(properties).build()));
    }
    return Optional.empty();
  }

  private SchemaModel wrapperSchema(SchemaModel dataSchema) {
    Map<String, SchemaModel> properties = new LinkedHashMap<>();
    properties.put("code", SchemaModel.builder().integerType().build());
    properties.put("msg", SchemaModel.builder().stringType().nullable(true).build());
    properties.put("data", dataSchema);
    return SchemaModel.builder()
        .objectType()
        .properties(properties)
        .build();
  }

  private Map<String, SchemaModel> singletonProperties(String key, SchemaModel value) {
    Map<String, SchemaModel> properties = new LinkedHashMap<>();
    properties.put(key, value);
    return properties;
  }
}
