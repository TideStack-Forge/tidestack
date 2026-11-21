package com.ouroboros.uischema;

import java.util.*;

import jakarta.annotation.Priority;

import org.springframework.stereotype.Component;

import com.ouroboros.uischema.service.CustomUISchemaService;

@Component
public class DBUISchemaProvider {

  private final CustomUISchemaService service;

  public DBUISchemaProvider(CustomUISchemaService service) {
    this.service = service;
    Proxy.provider = this;
  }

  public Optional<Object> getSchema(String path) {
    try {
      var schemas = service.searchAll();
      return schemas.stream()
          .filter(schema -> Objects.equals(schema.getPath(), path))
          .filter(schema -> schema.getEnabled() != null && schema.getEnabled())
          .findAny()
          .map(schema -> (Object) schema.getSchema());
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public Map<String, Object> getSchemaMap() {
    try {
      var schemas = service.searchAll();
      return schemas.stream().filter(schema ->
          schema.getEnabled() != null && schema.getEnabled()
      ).collect(
          () -> (Map<String, Object>) new HashMap<String, Object>(),
          (map, schema) -> map.put(schema.getPath(), schema.getSchema()),
          Map::putAll
      );
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  @Priority(200)
  public static class Proxy implements UISchemaProvider {
    private static DBUISchemaProvider provider;

    @Override
    public Optional<Object> getUISchema(String path) {
      return Optional.ofNullable(provider).flatMap(provider -> provider.getSchema(path));
    }

    @Override
    public Map<String, Object> getUISchemaMap() {
      return Optional.ofNullable(provider).map(DBUISchemaProvider::getSchemaMap).orElse(Collections.emptyMap());
    }
  }
}
