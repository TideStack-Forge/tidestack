package com.ouroboros.widget.manage.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.SPI;
import com.ouroboros.widget.WidgetDefinitionGenerator;
import com.ouroboros.widget.WidgetSchema;
import com.ouroboros.widget.event.WidgetSchemaRefreshed;
import com.ouroboros.widget.manage.model.WidgetSchemaModel;
import com.ouroboros.widget.manage.service.WidgetSchemaService;
import com.ouroboros.widget.service.WidgetDefinitionService;

@Service
@CacheConfig(cacheNames = "ouroboros:widget-schema")
public class WidgetSchemaServiceImpl implements WidgetSchemaService {
  private static final List<WidgetDefinitionGenerator> WIDGET_DEFINITION_GENERATORS = SPI.getSortedServiceStream(WidgetDefinitionGenerator.class)
      .collect(Collectors.toList());

  @Autowired
  private WidgetSchemaServiceImpl self;
  @Autowired
  private WidgetDefinitionService widgetDefinitionService;
  @Autowired
  ApplicationEventPublisher applicationEventPublisher;

  @Autowired
  CacheManager cacheManager;

  @Autowired
  private TypedDataModel<String, WidgetSchemaModel> dataModel;

  @Override
  public boolean checkNameAvailable(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    var hadDef = widgetDefinitionService.getAllWidgetDefinitions()
        .stream()
        .anyMatch(def -> def.getName().equals(name));
    return !hadDef;
  }

  @Override
  public List<WidgetDefinitionGenerator> getWidgetDefinitionGenerators() {
    return Collections.unmodifiableList(WIDGET_DEFINITION_GENERATORS);
  }

  @Override
  public List<SchemaNode> getFormItems(String generatorName) {
    return WIDGET_DEFINITION_GENERATORS.stream()
        .filter(generator -> generator.getName().equals(generatorName))
        .findFirst()
        .map(WidgetDefinitionGenerator::getFormItems)
        .orElseGet(Collections::emptyList);
  }

  @Override
  public Try<Void> addWidgetSchema(WidgetSchema widgetSchema) {
    var data = WidgetSchemaModel.fromWidgetSchema(widgetSchema);
    data.setCreatedAt(LocalDateTime.now());
    data.setCreatedBy(getCurrentUserIdentity());
    return getDataModel()
        .mapTry(model -> model.insert(data).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onSuccess(v -> triggerRefresh())
        .map(v -> null);
  }

  @Override
  public Try<Void> updateWidgetSchema(String id, WidgetSchema widgetSchema) {
    var data = WidgetSchemaModel.fromWidgetSchema(widgetSchema)
        .setId(id)
        .setUpdatedAt(LocalDateTime.now())
        .setUpdatedBy(getCurrentUserIdentity());
    return getDataModel()
        .mapTry(model -> model.update(id, data).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onSuccess(v -> triggerRefresh())
        .map(v -> null);
  }

  @Override
  public Try<Void> deleteWidgetSchema(String id) {
    return getDataModel()
        .mapTry(model -> model.delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .onSuccess(v -> triggerRefresh())
        .map(v -> null);
  }

  @Override
  public Optional<WidgetSchema> getWidgetSchema(String id) {
    return self.getAllWidgetSchemas()
        .stream()
        .filter(model -> model.getId().equals(id))
        .findFirst()
        .map(WidgetSchemaModel::toWidgetSchema);
  }

  @Override
  @Cacheable(sync = true)
  public List<WidgetSchemaModel> getAllWidgetSchemas() {
    return getDataModel()
        .mapTry(model -> model.query(Collections.emptyMap())
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .getOrElse(Collections::emptyList);
  }

  private Try<TypedDataModel<String, WidgetSchemaModel>> getDataModel() {
    return Try.success(dataModel);
  }

  private String getCurrentUserIdentity() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
  }

  private void triggerRefresh() {
    var cache = cacheManager.getCache("ouroboros:widget-schema");
    if (cache != null) {
      cache.clear();
    }
    applicationEventPublisher.publishEvent(new WidgetSchemaRefreshed(this));
  }
}
